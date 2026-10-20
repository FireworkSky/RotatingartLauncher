#!/usr/bin/env python3
"""把编译好的 Localizer.dll 打成 tModLoader 0.11 的 .tmod

布局（对应 TmodFile.Write）：
    "TMOD" + tML 版本字符串 + [20B SHA1][256B 签名占位][4B 数据区长度]
    + 名称 + mod 版本 + 条目表(name/原始长/压缩长) + 数据
本脚本统一不压缩（ulen == clen），tML 0.11 能直接读。
"""
import struct, hashlib, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent

def varint(n):
    out = b''
    while True:
        b = n & 0x7f
        n >>= 7
        out += bytes([b | 0x80]) if n else bytes([b])
        if not n:
            return out

def wstr(s):
    b = s.encode('utf8') if isinstance(s, str) else s
    return varint(len(b)) + b

def pack(outpath, name, version, tml_version, entries):
    table = b''
    data = b''
    for en, blob in entries:
        table += wstr(en) + struct.pack('<ii', len(blob), len(blob))
        data += blob
    body = wstr(name) + wstr(version) + struct.pack('<i', len(entries)) + table + data
    head = b'TMOD' + wstr(tml_version) + hashlib.sha1(body).digest() + bytes(256) + struct.pack('<i', len(body))
    Path(outpath).write_bytes(head + body)
    print(f"packed {outpath}: {len(head) + len(body)} bytes, {len(entries)} entries")

def main():
    out = ROOT / 'out' / 'Localizer.tmod'
    dll = ROOT / 'out' / 'Localizer.dll'
    if not dll.exists():
        sys.exit('缺少 out/Localizer.dll，先跑 ./build.sh 里的 mcs 步骤')
    entries = [
        ('Info', (ROOT / 'Info').read_bytes()),
        ('!Localizer.FNA.dll', dll.read_bytes()),
        ('lib/0Harmony.dll', (ROOT / 'libs' / '0Harmony.dll').read_bytes()),
        ('lib/Ninject.dll', (ROOT / 'libs' / 'Ninject.dll').read_bytes()),
        ('lib/Squid.dll', (ROOT / 'libs' / 'Squid.dll').read_bytes()),
    ]
    pack(out, '!Localizer', '1.5.0.19', '0.11.8.11', entries)

if __name__ == '__main__':
    main()
