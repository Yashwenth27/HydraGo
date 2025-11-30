import json
from bs4 import BeautifulSoup

def clean_text(text):
    """Helper to clean whitespace and unicode characters."""
    if not text:
        return None
    return " ".join(text.split())

def parse_metro_route(html_content):
    """
    Parses yometro.com HTML to extract route info, stats, timings, fares, and station list.
    """
    soup = BeautifulSoup(html_content, 'html.parser')
    data = {
        "basic_info": {},
        "journey_stats": {},
        "timings": {},
        "fares": {},
        "route_sequence": [],
        "fare_chart": []
    }

    # ==========================================
    # 1. & 5. Route Sequence & Basic Info (Interchange Support)
    # ==========================================
    # The route is contained in a div with style="line-height:1;"
    # We iterate through children to handle line changes dynamically.
    route_container = soup.find('div', style="line-height:1;")
    
    if route_container:
        current_leg = {
            "line_info": None,
            "stations": []
        }
        
        # We start looking for the start of the trip
        start_found = False
        
        for element in route_container.children:
            if element.name != 'div':
                continue
            
            classes = element.get('class', [])
            
            # Detect Trip Start or Interchange ("Change here")
            # Note: "tripstart" and "changetrain" are inside "addinfoLeft" wrapper usually, 
            # but sometimes checking text or inner classes is safer.
            inner_html = str(element)
            
            if 'tripstart' in inner_html:
                start_found = True
                continue
            
            # Line Information (e.g., "Red Line Platform 2...")
            if 'addinfoRight' in classes:
                # If we already have stations in the current leg, push it to main list
                # This happens if there was a previous leg
                if current_leg["line_info"] and current_leg["stations"]:
                    data["route_sequence"].append(current_leg)
                    current_leg = {"line_info": None, "stations": []}
                
                current_leg["line_info"] = clean_text(element.get_text())

            # Station Name
            elif 'stationblock1' in classes:
                station_name = clean_text(element.get_text().replace('◉', '').strip())
                if station_name:
                    current_leg["stations"].append(station_name)

            # Detect Interchange Trigger
            elif 'changetrain' in inner_html:
                # We save the current leg and prepare for the next
                if current_leg["line_info"] or current_leg["stations"]:
                    data["route_sequence"].append(current_leg)
                    current_leg = {"line_info": None, "stations": []}

            # Trip End
            elif 'tripend' in classes:
                break
        
        # Append the final leg
        if current_leg["line_info"] or current_leg["stations"]:
            data["route_sequence"].append(current_leg)

        # set Source and Destination from the sequence
        if data["route_sequence"]:
            data["basic_info"]["source"] = data["route_sequence"][0]["stations"][0]
            data["basic_info"]["destination"] = data["route_sequence"][-1]["stations"][-1]

    # ==========================================
    # 2., 3., 4. Statistics, Timings, and Fares
    # ==========================================
    # These are located in .info-box and .info-box1 containers
    # We map the "Label" text to our JSON keys
    
    key_map = {
        "Travel Time": ("journey_stats", "travel_time"),
        "Travel Distance": ("journey_stats", "distance"),
        "Total Stations": ("journey_stats", "total_stops"),
        "Interchanges": ("journey_stats", "interchanges"),
        
        "First Metro": ("timings", "first_metro"),
        "Last Metro": ("timings", "last_metro"),
        "Time Limit (Mins.)": ("timings", "time_limit_mins"),
        
        "Token Fare": ("fares", "token_fare"),
        "Smart Card Fare": ("fares", "smart_card_fare"),
        "Concessional Fare": ("fares", "concessional_fare"),
    }

    # Find all boxes that might contain info
    info_items = soup.select('.info-item, .info-item1')
    
    for item in info_items:
        label_tag = item.find('span', class_='label')
        value_tag = item.find('div', class_='value')
        
        if label_tag and value_tag:
            label = clean_text(label_tag.get_text())
            value = clean_text(value_tag.get_text())
            
            # Map scraping label to our data structure
            if label in key_map:
                category, json_key = key_map[label]
                data[category][json_key] = value

    # ==========================================
    # 4. Fare Chart (Table)
    # ==========================================
    fare_table = soup.find('table', class_='table-fare')
    if fare_table:
        # Get headers
        headers = [clean_text(th.get_text()) for th in fare_table.select('thead th')]
        
        # Get rows
        rows = fare_table.select('tbody tr')
        for row in rows:
            cols = [clean_text(td.get_text()) for td in row.find_all('td')]
            if len(cols) == len(headers):
                entry = dict(zip(headers, cols))
                data["fare_chart"].append(entry)

    return data

# ==========================================
# FLASK API WRAPPER
# ==========================================
# You can run this file directly to start the API
from flask import Flask, request, jsonify
import requests

app = Flask(__name__)

@app.route('/scrape-route', methods=['GET'])
def get_route_info():
    """
    API Endpoint. 
    Usage: /scrape-route?url=https://yometro.com/from-nampally...
    """
    target_url = request.args.get('url')
    
    if not target_url:
        return jsonify({"error": "Please provide a 'url' query parameter"}), 400

    try:
        # 1. Fetch the data (simulated here with the user provided logic)
        headers = {'User-Agent': 'Mozilla/5.0'}
        response = requests.get(target_url, headers=headers)
        
        if response.status_code != 200:
             return jsonify({"error": "Failed to fetch URL"}), 500

        # 2. Parse the data
        scraped_data = parse_metro_route(response.content)
        
        return jsonify(scraped_data)

    except Exception as e:
        return jsonify({"error": str(e)}), 500

# ==========================================
# TEST BLOCK (For local testing without Flask)
# ==========================================
if __name__ == "__main__":
    # Example: Paste your HTML content into a file or variable to test locally
    # For now, I will demonstrate how the parser processes the text provided in your prompt.
    
    print("Starting API Server...")
    # Set use_reloader=False to stop the infinite loop
    app.run(debug=True, use_reloader=False, port=5000)
