# -*- coding: utf-8 -*-
"""Семя -> ibcmd (конфигурация с языком обычным import, объекты - import files --no-check) -> export.

python platform.py <ibcmd.exe> <семя> <рабочий каталог (короткий путь)>
"""
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

ibcmd, seed, work = sys.argv[1], Path(sys.argv[2]), Path(sys.argv[3])
shutil.rmtree(work, ignore_errors=True)
pre, out, data, db = (work / x for x in ("pre", "out", "d", "b"))
pre.mkdir(parents=True)
shutil.copytree(seed / "Languages", pre / "Languages")
cfg = (seed / "Configuration.xml").read_text(encoding="utf-8-sig")
cfg_pre = re.sub(r"<ChildObjects>.*?</ChildObjects>",
                 "<ChildObjects>\n\t\t\t<Language>Русский</Language>\n\t\t</ChildObjects>", cfg, flags=re.S)
(pre / "Configuration.xml").write_text("﻿" + cfg_pre, encoding="utf-8")
seed_copy = work / "seed"
shutil.copytree(seed, seed_copy)


def ib(*args):
    r = subprocess.run([ibcmd, *args], capture_output=True)
    text = (r.stdout + r.stderr).decode("utf-8", errors="replace")
    print("$", " ".join(args[:3]), "-> rc", r.returncode)
    print(text.strip()[-3000:])
    if r.returncode:
        sys.exit(r.returncode)


common = [f"--data={data}", f"--db-path={db}"]
ib("infobase", "create", *common, "--locale=ru")
ib("infobase", "config", "import", *common, str(pre))
files = [str(p) for p in seed_copy.rglob("*") if p.is_file()]
ib("infobase", "config", "import", "files", *common, f"--base-dir={seed_copy}", "--no-check", *files)
ib("infobase", "config", "export", *common, str(out))
(out / "ConfigDumpInfo.xml").unlink(missing_ok=True)
shutil.rmtree(data, ignore_errors=True)
shutil.rmtree(db, ignore_errors=True)
print("выгрузка:", out)
