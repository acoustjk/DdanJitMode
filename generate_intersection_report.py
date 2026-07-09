import urllib.parse
import urllib.request
import json
import ssl
from collections import defaultdict

context = ssl._create_unverified_context()

api_key = "5c23042252aba05d70cfe489fd7a05cda03f8ba53fedd20f172ce8aa98f1e536"

print("Downloading full intersection database page-by-page (Flat JSON structure)...")

item_list = []
page_no = 1

try:
    while True:
        # 공공데이터포털 1회 조회 한계인 1000개씩 페이지네이션하여 순차 요청
        query_string = f"serviceKey={api_key}&pageNo={page_no}&numOfRows=1000&type=json"
        url = f"http://apis.data.go.kr/B551982/rti/crsrd_map_info?{query_string}"
        
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, context=context, timeout=15) as response:
            content = response.read().decode('utf-8')
            data = json.loads(content)
            
            # response 래퍼가 없으므로 루트에서 바로 body 조회
            body = data.get("body", {})
            if not body:
                print(f"Page {page_no}: Empty body. Stopping pagination.")
                break
                
            items = body.get("items", {})
            if not items or not isinstance(items, dict):
                print(f"Page {page_no}: No items dictionary found. Stopping.")
                break
                
            page_item_val = items.get("item", [])
            page_items = []
            if isinstance(page_item_val, list):
                page_items = page_item_val
            elif isinstance(page_item_val, dict):
                page_items = [page_item_val]
                
            if not page_items:
                print(f"Page {page_no}: No items inside list. Stopping.")
                break
                
            item_list.extend(page_items)
            print(f"Page {page_no}: Successfully fetched {len(page_items)} intersections. (Accumulated: {len(item_list)})")
            
            # 수집된 양이 totalCount에 도달했거나 더 이상 받아올 데이터가 적으면 종료
            total_count_reported = body.get("totalCount", 0)
            if len(item_list) >= total_count_reported or len(page_items) < 1000:
                print(f"Fetch completed. Total reported: {total_count_reported}, Accumulated: {len(item_list)}")
                break
                
            page_no += 1
            
    total_count = len(item_list)
    
    if total_count > 0:
        # 1. 지자체(lclgvNm)별로 그룹핑
        region_map = defaultdict(list)
        for item in item_list:
            region = item.get("lclgvNm", "미분류").strip()
            region_map[region].append(item)
            
        # 2. 텍스트 리포트 생성 (supported_intersections.txt)
        txt_file = "supported_intersections.txt"
        with open(txt_file, "w", encoding="utf-8") as f:
            f.write("========================================================================\n")
            f.write(f" [교통안전 신호등 실시간 정보] 지원 교차로 리스트 요약 레포트\n")
            f.write(f" (총 {total_count}개 스마트 교차로 연동 중)\n")
            f.write("========================================================================\n\n")
            
            for region in sorted(region_map.keys()):
                intersections = region_map[region]
                sorted_intersections = sorted(intersections, key=lambda x: x.get("crsrdNm", ""))
                
                f.write(f"■ {region} (총 {len(intersections)}개 교차로 연동)\n")
                f.write("-" * 50 + "\n")
                for item in sorted_intersections:
                    crsrd_id = item.get("crsrdId", "N/A")
                    crsrd_nm = item.get("crsrdNm", "N/A")
                    lat = item.get("mapCtptIntLat", "0.0")
                    lng = item.get("mapCtptIntLot", "0.0")
                    f.write(f"  - {crsrd_nm} (ID: {crsrd_id}) | 좌표: 위도 {lat}, 경도 {lng}\n")
                f.write("\n")
                
        print(f"Text report saved to: {txt_file}")
        
        # 3. CSV 엑셀 리포트 생성 (supported_intersections.csv)
        csv_file = "supported_intersections.csv"
        with open(csv_file, "w", encoding="utf-8-sig") as f:
            f.write("관할 지자체,교차로명,교차로 ID,위도(Latitude),경도(Longitude)\n")
            for item in item_list:
                region = item.get("lclgvNm", "미분류").replace(",", " ").strip()
                name = item.get("crsrdNm", "미분류").replace(",", " ").strip()
                crsrd_id = item.get("crsrdId", "N/A").strip()
                lat = item.get("mapCtptIntLat", "0.0").strip()
                lng = item.get("mapCtptIntLot", "0.0").strip()
                f.write(f"{region},{name},{crsrd_id},{lat},{lng}\n")
                
        print(f"CSV spreadsheet report saved to: {csv_file}")
    else:
        print("Error: No intersections collected during loop.")
        
except Exception as e:
    print(f"Failed to generate reports: {e}")
