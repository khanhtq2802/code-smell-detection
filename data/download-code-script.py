import logging
import requests
import csv
import os
import re



# input_csv = "/code-smell-detection/data/MLCQCodeSmellSamples.csv"
# unique_sample_ids = set()

# with open(input_csv, newline='', encoding='utf-8') as csvfile:
#     reader = csv.DictReader(csvfile, delimiter=';')
#     for row in reader:
#         unique_sample_ids.add(row['sample_id'])

# print(f"Số lượng sample_id duy nhất: {len(unique_sample_ids)}")

# log_path = "/code-smell-detection/data/download_log.txt"
# logging.basicConfig(
#     filename=log_path,
#     filemode='a',
#     format='%(asctime)s %(levelname)s %(message)s',
#     level=logging.INFO
# )

# input_csv = "/code-smell-detection/data/MLCQCodeSmellSamples.csv"
# output_dir = "/code-smell-detection/data/code"

# os.makedirs(output_dir, exist_ok=True)

# downloaded = set()

# with open(input_csv, newline='', encoding='utf-8') as csvfile:
#     reader = csv.DictReader(csvfile, delimiter=';')
#     for row in reader:
#         sample_id = row['sample_id']
#         url = row['link']
#         if sample_id in downloaded:
#             continue
#         match = re.match(r"https://github.com/([^/]+)/([^/]+)/blob/([^/]+)/(.*?)(?:/#L\d+(?:-L\d+)?)?$", url)
#         if match:
#             owner, repo, commit, path = match.groups()[:4]
#             raw_url = f"https://raw.githubusercontent.com/{owner}/{repo}/{commit}/{path}"
#             resp = requests.get(raw_url)
#             if resp.status_code == 200:
#                 out_path = os.path.join(output_dir, f"{sample_id}.java")
#                 with open(out_path, "w", encoding="utf-8") as f:
#                     f.write(resp.text)
#                 print(f"Đã lưu {out_path}")
#                 logging.info(f"SUCCESS\tsample_id={sample_id}\traw_url={raw_url}")
#                 downloaded.add(sample_id)
#             else:
#                 print(f"Không thể tải file cho sample_id {sample_id}: {resp.status_code}, raw_url: {raw_url}")
#                 logging.error(f"FAIL\tsample_id={sample_id}\traw_url={raw_url}\tstatus_code={resp.status_code}")
#         else:
#             print(f"URL không hợp lệ cho sample_id {sample_id}")
#             logging.error(f"INVALID_URL\tsample_id={sample_id}\turl={url}")