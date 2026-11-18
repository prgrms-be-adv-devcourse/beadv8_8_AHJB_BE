# 상품 검색용 Elasticsearch
# 한국어 형태소 분석(nori) 플러그인을 미리 설치한 이미지
# 버전은 Spring Boot 4.1.1이 쓰는 Elasticsearch 클라이언트(9.4.5)와 맞춘다.
FROM docker.elastic.co/elasticsearch/elasticsearch:9.4.5

RUN bin/elasticsearch-plugin install --batch analysis-nori