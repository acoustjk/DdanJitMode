import urllib.parse
import urllib.request
import json
import ssl

context = ssl._create_unverified_context()

api_key = "5c23042252aba05d70cfe489fd7a05cda03f8ba53fedd20f172ce8aa98f1e536"

# 신내차량기지 교차로(crsrdId: 1068)의 실시간 신호 데이터를 조회합니다
query_string = f"serviceKey={api_key}&crsrdId=1068&pageNo=1&numOfRows=10&type=json"
url = f"http://apis.data.go.kr/B551982/rti/tl_drct_info?{query_string}"

print(f"Calling URL: http://apis.data.go.kr/B551982/rti/tl_drct_info (ServiceKey hidden)")

try:
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req, context=context, timeout=15) as response:
        content = response.read().decode('utf-8')
        print("Success! Data received from server.")
        
        # 파일 저장
        output_file = "signal_status_raw.json"
        with open(output_file, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Raw response saved to: {output_file}")
        
        # JSON 분석
        try:
            data = json.loads(content)
            response_obj = data.get("response", {})
            header = response_obj.get("header", {})
            print(f"Header Result -> Code: {header.get('resultCode')}, Msg: {header.get('resultMsg')}")
            
            body = response_obj.get("body", {})
            items = body.get("items", {}) if body else {}
            item_list = []
            if items:
                item_val = items.get("item", [])
                if isinstance(item_val, list):
                    item_list = item_val
                elif isinstance(item_val, dict):
                    item_list = [item_val]
            
            print(f"Total Signal Directions Fetched: {len(item_list)}")
            if len(item_list) > 0:
                print("\nSample Signal Item Structure:")
                print(json.dumps(item_list[0], indent=2, ensure_ascii=False))
        except Exception as pe:
            print(f"JSON analysis failed: {pe}. Raw text preview:")
            print(content[:800])
except Exception as e:
    print(f"HTTP Request failed: {e}")
