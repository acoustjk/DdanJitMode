import urllib.parse
import urllib.request
import json
import ssl

context = ssl._create_unverified_context()

api_key = "5c23042252aba05d70cfe489fd7a05cda03f8ba53fedd20f172ce8aa98f1e536"

# 404가 발생하지 않도록 실제 규격인 crsrd_map_info 오퍼레이션을 호출합니다
query_string = f"serviceKey={api_key}&pageNo=1&numOfRows=1000&type=json"
url = f"http://apis.data.go.kr/B551982/rti/crsrd_map_info?{query_string}"

print(f"Calling URL: http://apis.data.go.kr/B551982/rti/crsrd_map_info (ServiceKey hidden)")

try:
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req, context=context, timeout=15) as response:
        content = response.read().decode('utf-8')
        print("Success! Data received from server.")
        
        # 파일 저장
        output_file = "intersection_list_raw.json"
        with open(output_file, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Raw response saved to: {output_file}")
        
        # JSON 분석 및 결과 요약
        try:
            data = json.loads(content)
            
            # 공공데이터 JSON 구조 분석
            response_obj = data.get("response", {})
            header = response_obj.get("header", {})
            result_code = header.get("resultCode")
            result_msg = header.get("resultMsg")
            print(f"Header Result -> Code: {result_code}, Msg: {result_msg}")
            
            body = response_obj.get("body", {})
            total_count = body.get("totalCount", 0) if body else 0
            print(f"Total count reported by server: {total_count}")
            
            items = body.get("items", {}) if body else {}
            item_list = []
            if items:
                # 단일 객체일 수도 있고 리스트일 수도 있으므로 방어 코드 작성
                item_val = items.get("item", [])
                if isinstance(item_val, list):
                    item_list = item_val
                elif isinstance(item_val, dict):
                    item_list = [item_val]
            
            print(f"Total Intersections Fetched in this page: {len(item_list)}")
            
            if len(item_list) > 0:
                print("\nSample Item Structure:")
                print(json.dumps(item_list[0], indent=2, ensure_ascii=False))
                
                # 키워드 매칭 테스트 (신내, 양주, 의정부, 도봉, 중랑, 노원, 강남)
                keywords = ["신내", "양주", "의정부", "도봉", "중랑", "노원", "강남"]
                print("\n--- Keyword Match Results ---")
                for kw in keywords:
                    # 교차로 이름 필드가 다를 수 있으므로 key 검색 후 매칭
                    name_keys = [k for k in item_list[0].keys() if "nm" in k.lower() or "name" in k.lower() or "itst" in k.lower()]
                    name_key = name_keys[0] if name_keys else None
                    
                    if name_key:
                        matches = [item for item in item_list if item.get(name_key) and kw in str(item.get(name_key))]
                        print(f"'{kw}' (using key '{name_key}'): Found {len(matches)} intersections")
                        for m in matches[:3]:
                            print(f"  - {m.get(name_key)} (ID: {m.get('itstId') or m.get('crsrdId') or m.get('id')})")
                    else:
                        # Fallback: 전체 값 문자열 검색
                        matches = [item for item in item_list if any(kw in str(v) for v in item.values())]
                        print(f"'{kw}' (Raw search): Found {len(matches)} intersections")
            
        except Exception as pe:
            print(f"JSON analysis failed: {pe}. Raw text preview (First 800 chars):")
            print(content[:800])
except Exception as e:
    print(f"HTTP Request failed: {e}")
