mixed-port: 7890
allow-lan: true
bind-address: "*"
find-process-mode: strict
mode: rule
unified-delay: false
tcp-concurrent: true
log-level: info
ipv6: true
global-client-fingerprint: chrome
external-controller: 127.0.0.1:9090
external-ui: ui
external-ui-url: "https://github.com/MetaCubeX/metacubexd/archive/refs/heads/gh-pages.zip"
tun:
  enable: true
  stack: mixed
  dns-hijack:
    - 0.0.0.0:53
  auto-detect-interface: true
  auto-route: true
  auto-redirect: true
  mtu: 9000
profile:
  store-selected: false
  store-fake-ip: true
sniffer:
  enable: true
  override-destination: false
  sniff:
    TLS:
      ports: [443, 8443]
    HTTP:
      ports: [80, 8080-8880]
      override-destination: true
    QUIC:
      ports: [443, 8443]
  skip-domain:
    - "+.push.apple.com"
dns:
  enable: true
  prefer-h3: false
  respect-rules: true
  listen: 0.0.0.0:53
  ipv6: true
  default-nameserver:
    - 223.5.5.5
  enhanced-mode: fake-ip
  fake-ip-range: 198.18.0.1/16
  fake-ip-filter-mode: blacklist
  fake-ip-filter:
    - "*"
    - "+.lan"
    - "+.local"
  nameserver-policy:
    "rule-set:cn_domain,private_domain":
      - https://120.53.53.53/dns-query
      - https://223.5.5.5/dns-query
    "rule-set:category-ads-all": 
      - rcode://success
    "rule-set:geolocation-!cn": 
      - "https://dns.cloudflare.com/dns-query"
      - "https://dns.google/dns-query"
  nameserver:
    - https://120.53.53.53/dns-query
    - https://223.5.5.5/dns-query
  proxy-server-nameserver:
    - https://120.53.53.53/dns-query
    - https://223.5.5.5/dns-query
proxies:
  - name: vless-reality-vision-2568
    type: vless
    server: 8.218.152.251
    port: 443
    uuid: d40d06b2-5a1c-4f5d-ba2d-11ec37ce6b75
    network: tcp
    udp: true
    tls: true
    flow: xtls-rprx-vision
    servername: www.microsoft.com
    reality-opts:
      public-key: jam28vDhHKtQ7pfbbuiuh1uNGIuKx0QdrOtcssdsjBo
      short-id: 78d3b88f4e5572b6

proxy-groups:
  - name: Proxy
    type: select
    proxies:
      - vless-reality-vision-2568
      - auto
  - name: auto
    type: url-test
    proxies:
      - vless-reality-vision-2568
    url: "https://cp.cloudflare.com/generate_204"
    interval: 300
rules:
  - RULE-SET,private_ip,DIRECT,no-resolve
  - RULE-SET,category-ads-all,REJECT
  - RULE-SET,cn_domain,DIRECT
  - RULE-SET,geolocation-!cn,Proxy
  - RULE-SET,cn_ip,DIRECT
  - MATCH,Proxy
rule-anchor:
  ip: &ip {type: http, interval: 86400, behavior: ipcidr, format: mrs}
  domain: &domain {type: http, interval: 86400, behavior: domain, format: mrs}
rule-providers:
  private_domain:
    <<: *domain
    url: "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/meta/geo/geosite/private.mrs"
  cn_domain:
    <<: *domain
    url: "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/meta/geo/geosite/cn.mrs"
  geolocation-!cn:
    <<: *domain
    url: "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/meta/geo/geosite/geolocation-!cn.mrs"
  category-ads-all:
    <<: *domain
    url: "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/meta/geo/geosite/category-ads-all.mrs"
  private_ip:
    <<: *ip
    url: "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/meta/geo/geoip/private.mrs"
  cn_ip:
    <<: *ip
    url: "https://raw.githubusercontent.com/MetaCubeX/meta-rules-dat/meta/geo/geoip/cn.mrs"