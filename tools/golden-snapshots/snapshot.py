#!/usr/bin/env python3
"""Служебные шаги съёмки эталонов для dump.sh: сборка семени, срезка вспомогательного, сверка.

Только стандартная библиотека Python 3. Файлы выгрузки правятся построчно, без пересериализации:
так сохраняются BOM, CRLF и форма пустых элементов, как их пишет платформа.

Команды:
  assemble  собрать семя под формат платформы (с зависимостями из seed-deps или без них;
            с --nodes - только виды-владельцы, каждому по узлу каждого вида)
  external-nodes  семя внешних отчёта и обработки с узлами (из seed-external)
  prephase  первая фаза маршрута import files --no-check: конфигурация только с языком и список файлов
  failed    виды, объекты которых названы в строках [ERROR] журнала импорта
  finish    разложить выгрузку: убрать вспомогательные объекты и срезать зависимости
  compare   сравнить два каталога побайтно (или с точностью до UUID)
"""
import argparse
import json
import re
import shutil
import sys
import uuid
from pathlib import Path

# С какого формата вид входит в состав конфигурации (выбор MetaDataObject в XSD)
KIND_SINCE = {"Bot": "2.11", "WebSocketClient": "2.20", "PaletteColor": "2.21"}

ROOT_RE = re.compile(r"<MetaDataObject\b[^>]*>\s*<(\w+)\b")
NAME_RE = re.compile(r"<Properties>\s*<Name>([^<]+)</Name>")
PROP_RE = re.compile(r"(?m)^\t\t\t<(\w+)[\s/>]")
UUID_RE = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
CHILD_OBJECTS_RE = re.compile(r"<ChildObjects/>|<ChildObjects>.*?</ChildObjects>", re.S)

# ---------- Узлы объектов (cf-object-nodes) ----------
# Узел в семени минимальный, каким его задаёт md-sparrow: имя, синоним на русском, пустой
# комментарий, тип строка 10 (у признаков учёта - булево), у команды группа FormCommandBarImportant.
# Остальные свойства дописывает платформа: это и есть эталон узла (cf/GoldenNodes в md-sparrow).
# Табличных частей две: пустая (эталон новой табличной части) и с реквизитом (эталон реквизита
# табличной части). Идентификаторы - uuid5 от имён владельца и узла: повтор даёт те же файлы.
NODE_NS = uuid.UUID("0b8f4c1e-5a53-4a44-9d0e-6f3c2a7b1d20")
STRING10 = ("<Type><v8:Type>xs:string</v8:Type><v8:StringQualifiers><v8:Length>10</v8:Length>"
            "<v8:AllowedLength>Variable</v8:AllowedLength></v8:StringQualifiers></Type>")
BOOLEAN = "<Type><v8:Type>xs:boolean</v8:Type></Type>"


def node_uuid(*parts):
    return str(uuid.uuid5(NODE_NS, "|".join(parts)))


def node_head(owner, tag, name, internal=""):
    return (f'<{tag} uuid="{node_uuid(owner, tag, name)}">{internal}<Properties><Name>{name}</Name>'
            f"<Synonym><v8:item><v8:lang>ru</v8:lang><v8:content>{name}</v8:content></v8:item></Synonym>"
            "<Comment/>")


def data_node(owner, tag, name, type_block=STRING10):
    return f"{node_head(owner, tag, name)}{type_block}</Properties></{tag}>"


def enum_value(owner, name):
    return f"{node_head(owner, 'EnumValue', name)}</Properties></EnumValue>"


def command(owner, name):
    return (f"{node_head(owner, 'Command', name)}<Group>FormCommandBarImportant</Group><CommandParameterType/>"
            "<ParameterUseMode>Single</ParameterUseMode><ModifiesData>false</ModifiesData>"
            "<Representation>Auto</Representation><ToolTip/><Picture/><Shortcut/>"
            "<OnMainServerUnavalableBehavior>Auto</OnMainServerUnavalableBehavior></Properties></Command>")


def tabular_section(kind, owner, name, inner=""):
    generated = "".join(
        f'<xr:GeneratedType name="{kind}{category}.{owner}.{name}" category="{category}">'
        f"<xr:TypeId>{node_uuid(owner, name, category, 'type')}</xr:TypeId>"
        f"<xr:ValueId>{node_uuid(owner, name, category, 'value')}</xr:ValueId></xr:GeneratedType>"
        for category in ("TabularSection", "TabularSectionRow"))
    children = f"<ChildObjects>{inner}</ChildObjects>" if inner else "<ChildObjects/>"
    head = node_head(owner, "TabularSection", name, f"<InternalInfo>{generated}</InternalInfo>")
    return f"{head}</Properties>{children}</TabularSection>"


def with_tabular(kind, owner):
    """Реквизит, пустая табличная часть и табличная часть с реквизитом."""
    return (data_node(owner, "Attribute", "Реквизит1")
            + tabular_section(kind, owner, "ТабличнаяЧасть1")
            + tabular_section(kind, owner, "ТабличнаяЧасть2",
                              data_node(owner + ".ТабличнаяЧасть2", "Attribute", "Реквизит1")))


def register(owner):
    return (data_node(owner, "Dimension", "Измерение1") + data_node(owner, "Resource", "Ресурс1")
            + data_node(owner, "Attribute", "Реквизит1"))


# Вид-владелец -> его узлы: те, что добавляет md-sparrow, у видов, чья схема их допускает
# (<Вид>ChildObjects XSD, от 2.10 до 2.21 не меняется).
NODES = {
    **{kind: lambda k, o: with_tabular(k, o) + command(o, "Команда1") for kind in (
        "Catalog", "Document", "DataProcessor", "Report", "ChartOfCharacteristicTypes",
        "ChartOfCalculationTypes", "ExchangePlan", "Task", "BusinessProcess")},
    "ChartOfAccounts": lambda k, o: (with_tabular(k, o)
                                     + data_node(o, "AccountingFlag", "ПризнакУчета1", BOOLEAN)
                                     + data_node(o, "ExtDimensionAccountingFlag", "ПризнакУчетаСубконто1", BOOLEAN)
                                     + command(o, "Команда1")),
    "Enum": lambda k, o: enum_value(o, "Значение1") + command(o, "Команда1"),
    "DocumentJournal": lambda k, o: command(o, "Команда1"),
    "FilterCriterion": lambda k, o: command(o, "Команда1"),
    **{kind: lambda k, o: register(o) + command(o, "Команда1") for kind in (
        "InformationRegister", "AccumulationRegister", "AccountingRegister", "CalculationRegister")},
    "Sequence": lambda k, o: data_node(o, "Dimension", "Измерение1"),
    # внешние: команд у них нет
    "ExternalDataProcessor": with_tabular,
    "ExternalReport": with_tabular,
}
# Пространства имён узлов: у минимального семени внешнего объекта их нет
NODE_NAMESPACES = {"v8": "http://v8.1c.ru/8.1/data/core", "xs": "http://www.w3.org/2001/XMLSchema",
                   "xsi": "http://www.w3.org/2001/XMLSchema-instance"}


def with_nodes(text, kind, name):
    """ChildObjects объекта заменяется узлами (у объекта из seed-deps - вместе с его зависимостями)."""
    new, count = CHILD_OBJECTS_RE.subn(
        lambda _: f"<ChildObjects>{NODES[kind](kind, name)}</ChildObjects>", text, count=1)
    if count != 1:
        fail(f"у {kind} {name} нет ChildObjects")
    for prefix, uri in NODE_NAMESPACES.items():
        if f"xmlns:{prefix}=" not in new:
            new = re.sub(r"<MetaDataObject\b", lambda m: f'{m.group(0)} xmlns:{prefix}="{uri}"', new, count=1)
    return new


def cmd_external_nodes(a):
    """Семя внешних отчёта и обработки с узлами: из seed-external, по файлу на объект."""
    out = Path(a.out)
    shutil.rmtree(out, ignore_errors=True)
    out.mkdir(parents=True)
    for src in sorted(Path(a.seed).glob("*.xml")):
        kind, name = describe(src)
        write(out / src.name, with_nodes(read(src), kind, name))


def fail(message):
    print(f"ERROR: {message}", file=sys.stderr)
    sys.exit(1)


def ver(value):
    return tuple(int(x) for x in value.split("."))


def read(path):
    return Path(path).read_bytes().decode("utf-8")


def write(path, text):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(text.encode("utf-8"))


def describe(path):
    """Вид и имя объекта по корневому элементу файла."""
    text = read(path)
    root, name = ROOT_RE.search(text), NAME_RE.search(text)
    if not root or not name:
        fail(f"не объект метаданных: {path}")
    return root.group(1), name.group(1)


def objects(tree):
    """Объекты верхнего уровня каталога выгрузки: {относительный путь: (вид, имя)}."""
    tree = Path(tree)
    res = {}
    for sub in sorted(p for p in tree.iterdir() if p.is_dir()):
        for f in sorted(sub.glob("*.xml")):
            res[f.relative_to(tree).as_posix()] = describe(f)
    return res


def properties_block(text):
    m = re.search(r"\t\t<Properties>(.*?)\t\t</Properties>", text, re.S)
    return m.group(1) if m else ""


def has_children(text):
    return bool(re.search(r"\t\t<ChildObjects>\r?\n", text))


def copy_object(src_root, rel, dst_root, text=None):
    """Файл объекта и его каталог (Ext, формы) из src_root в dst_root."""
    src, dst = Path(src_root) / rel, Path(dst_root) / rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    if text is None:
        shutil.copy2(src, dst)
    else:
        write(dst, text)
    own_dir = src.with_suffix("")
    if own_dir.is_dir():
        shutil.copytree(own_dir, dst.with_suffix(""), dirs_exist_ok=True)


def set_children(cfg_text, entries):
    """Заменить состав ChildObjects конфигурации (отступы как у платформы)."""
    nl = "\r\n" if "\r\n" in cfg_text else "\n"
    body = nl.join(f"\t\t\t<{k}>{n}</{k}>" for k, n in entries)
    new, count = re.subn(r"\t\t<ChildObjects>.*?\t\t</ChildObjects>",
                         lambda _: f"\t\t<ChildObjects>{nl}{body}{nl}\t\t</ChildObjects>", cfg_text, flags=re.S)
    if count != 1:
        fail("в Configuration.xml нет ChildObjects")
    return new


def children_of(cfg_text):
    m = re.search(r"\t\t<ChildObjects>(.*?)\t\t</ChildObjects>", cfg_text, re.S)
    return re.findall(r"<(\w+)>([^<]+)</\1>", m.group(1)) if m else []


def cmd_assemble(a):
    seed, deps, out = Path(a.seed), Path(a.deps), Path(a.out)
    fmt = a.format
    exclude = set(filter(None, (a.exclude or "").split(",")))
    shutil.rmtree(out, ignore_errors=True)
    out.mkdir(parents=True)
    seed_objs = objects(seed)
    deps_objs = objects(deps) if deps.is_dir() else {}
    kinds, skipped, strip, aux = {}, {}, {}, []
    for rel, (kind, name) in seed_objs.items():
        if a.nodes and kind != "Language" and kind not in NODES:
            continue
        if kind in KIND_SINCE and ver(fmt) < ver(KIND_SINCE[kind]):
            skipped[kind] = f"вида нет в формате {fmt} (появился в {KIND_SINCE[kind]})"
            continue
        if kind in exclude:
            skipped[kind] = "НЕ СНЯТ: обычный import его не принял, а import files --no-check в ibcmd нет"
            continue
        text = None
        route = "import"
        if a.with_deps and rel in deps_objs:
            text = read(deps / rel)
            base = read(seed / rel)
            base_props = set(PROP_RE.findall(properties_block(base)))
            extra = [p for p in PROP_RE.findall(properties_block(text)) if p not in base_props]
            # дочерние объекты зависимости заменяются узлами: срезать их не нужно
            children = has_children(text) and not has_children(base) and not a.nodes
            strip[rel] = {"props": extra, "children": children}
            cut = extra + (["ChildObjects"] if children else [])
            route = "import + срезка (" + ", ".join(cut) + ")" if cut else "import"
        if a.nodes and kind in NODES:
            text = with_nodes(read(seed / rel) if text is None else text, kind, name)
        copy_object(seed, rel, out, text)
        if kind != "Language":
            kinds[kind] = {"name": name, "path": rel, "route": route}
    if a.with_deps:
        for rel, (kind, name) in deps_objs.items():
            if rel in seed_objs:
                continue
            text = read(deps / rel)
            # кому нужен вспомогательный объект: кто ссылается на него и на кого ссылается он сам
            users = {seed_objs[s][0] for s in strip if re.search(rf"\.{re.escape(name)}(?![\w])", read(out / s))}
            for s, (k, n) in seed_objs.items():
                if not re.search(rf"(?<![\w.]){k}\.{re.escape(n)}(?![\w])", text):
                    continue
                if s in strip:
                    users.add(k)
                elif k not in kinds:
                    # ссылка на вид, которого в этом прогоне нет, платформа не примет
                    text = re.sub(rf"(?m)^[ \t]*<xr:Item [^>]*>{k}\.{re.escape(n)}</xr:Item>\r?\n", "", text)
            if not users:
                continue
            text = re.sub(r"(?m)^([ \t]*)<(\w+)>\r?\n[ \t]*</\2>", r"\1<\2/>", text)
            copy_object(deps, rel, out, text)
            aux.append({"kind": kind, "name": name, "path": rel, "for": sorted(users)})
    cfg = read(seed / "Configuration.xml")
    entries = [(k, n) for k, n in children_of(cfg) if k == "Language" or k in kinds]
    for item in aux:
        same = [i for i, (k, _) in enumerate(entries) if k == item["kind"]]
        pos = same[-1] + 1 if same else len(entries)
        entries.insert(pos, (item["kind"], item["name"]))
    write(out / "Configuration.xml", set_children(cfg, entries))
    manifest = {"format": fmt, "kinds": kinds, "skipped": skipped, "strip": strip, "aux": aux}
    Path(a.manifest).write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")


def cmd_prephase(a):
    src, pre = Path(a.src), Path(a.pre)
    shutil.rmtree(pre, ignore_errors=True)
    pre.mkdir(parents=True)
    cfg = read(src / "Configuration.xml")
    write(pre / "Configuration.xml", set_children(cfg, [e for e in children_of(cfg) if e[0] == "Language"]))
    shutil.copytree(src / "Languages", pre / "Languages")
    files = [src / "Configuration.xml"] + sorted(
        p for p in src.rglob("*") if p.is_file() and p.parent != src and p.relative_to(src).parts[0] != "Languages")
    Path(a.list).write_text("\n".join(str(p.resolve()) for p in files) + "\n", encoding="utf-8")


def cmd_failed(a):
    manifest = json.loads(Path(a.manifest).read_text(encoding="utf-8"))
    log = Path(a.log).read_bytes().decode("utf-8", errors="replace")
    names = {v["name"]: k for k, v in manifest["kinds"].items()}
    for item in manifest["aux"]:
        names[item["name"]] = item["for"]
    found = set()
    for line in log.splitlines():
        if "ERROR" not in line and "Ошибка" not in line:
            continue
        for name, kind in names.items():
            if re.search(rf"\.{re.escape(name)}(?![\w])", line):
                found.update(kind if isinstance(kind, list) else [kind])
    print(",".join(sorted(found)))


def strip_object(text, props, children):
    for p in props:
        pat = re.compile(rf"(?ms)^(\t\t\t)<{p}>(?:[^<\r\n]*|\r?\n.*?\r?\n\t\t\t)</{p}>")
        text, n = pat.subn(lambda m: f"{m.group(1)}<{p}/>", text, count=1)
        if n == 0 and not re.search(rf"(?m)^\t\t\t<{p}/>", text):
            raise ValueError(f"нет свойства {p}")
    if children:
        text, n = re.subn(r"(?s)(\t\t)<ChildObjects>\r?\n.*?\r?\n\t\t</ChildObjects>",
                          lambda m: f"{m.group(1)}<ChildObjects/>", text, count=1)
        if n == 0:
            raise ValueError("нет ChildObjects")
    return text


def cmd_finish(a):
    export, dest = Path(a.export), Path(a.dest)
    manifest = json.loads(Path(a.manifest).read_text(encoding="utf-8"))
    shutil.rmtree(dest, ignore_errors=True)
    shutil.copytree(export, dest, ignore=shutil.ignore_patterns("ConfigDumpInfo.xml"))
    errors = []
    for item in manifest["aux"]:
        f = dest / item["path"]
        f.unlink(missing_ok=True)
        shutil.rmtree(f.with_suffix(""), ignore_errors=True)
        if not any(f.parent.iterdir()):
            f.parent.rmdir()
    cfg_path = dest / "Configuration.xml"
    cfg = read(cfg_path)
    for item in manifest["aux"]:
        cfg = re.sub(rf"(?m)^\t\t\t<{item['kind']}>{re.escape(item['name'])}</{item['kind']}>\r?\n", "", cfg)
    write(cfg_path, cfg)
    for rel, rule in manifest["strip"].items():
        f = dest / rel
        if not f.exists():
            continue
        try:
            write(f, strip_object(read(f), rule["props"], rule["children"]))
        except ValueError as e:
            errors.append(f"{rel}: {e}")
    lines = []
    for kind in sorted(manifest["kinds"]):
        info = manifest["kinds"][kind]
        route = a.route or info["route"]
        state = "снят" if (dest / info["path"]).exists() else "НЕ ВЫГРУЖЕН"
        if state != "снят":
            errors.append(f"{info['path']}: платформа не выгрузила объект")
        lines.append(f"  {kind:<28} {state}: {route}")
    for kind in sorted(manifest["skipped"]):
        lines.append(f"  {kind:<28} пропущен: {manifest['skipped'][kind]}")
    print("\n".join(lines))
    if errors:
        print("ERROR: " + "; ".join(errors), file=sys.stderr)
        sys.exit(1)


def normalized(text):
    seen = {}
    return UUID_RE.sub(lambda m: seen.setdefault(m.group(0), f"UUID{len(seen):03d}"), text)


def cmd_compare(a):
    left, right = Path(a.left), Path(a.right)
    files = lambda root: {p.relative_to(root).as_posix() for p in root.rglob("*") if p.is_file()}
    lf, rf = files(left), files(right)
    same = diff = 0
    for rel in sorted(lf | rf):
        if rel not in lf or rel not in rf:
            print(f"  {'ТОЛЬКО СЛЕВА' if rel in lf else 'ТОЛЬКО СПРАВА'} {rel}")
            diff += 1
            continue
        x, y = (left / rel).read_bytes(), (right / rel).read_bytes()
        if a.uuid:
            x, y = normalized(x.decode("utf-8")), normalized(y.decode("utf-8"))
        if x == y:
            same += 1
        else:
            diff += 1
            print(f"  РАЗЛИЧАЕТСЯ {rel}")
    print(f"совпало {same}, различий {diff}")
    sys.exit(0 if diff == 0 else 2)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("assemble")
    s.add_argument("--seed", required=True)
    s.add_argument("--deps", required=True)
    s.add_argument("--out", required=True)
    s.add_argument("--manifest", required=True)
    s.add_argument("--format", required=True)
    s.add_argument("--with-deps", action="store_true")
    s.add_argument("--nodes", action="store_true", help="только виды-владельцы, каждому по узлу каждого вида")
    s.add_argument("--exclude")
    s.set_defaults(fn=cmd_assemble)
    s = sub.add_parser("external-nodes")
    s.add_argument("--seed", required=True)
    s.add_argument("--out", required=True)
    s.set_defaults(fn=cmd_external_nodes)
    s = sub.add_parser("prephase")
    s.add_argument("--src", required=True)
    s.add_argument("--pre", required=True)
    s.add_argument("--list", required=True)
    s.set_defaults(fn=cmd_prephase)
    s = sub.add_parser("failed")
    s.add_argument("--log", required=True)
    s.add_argument("--manifest", required=True)
    s.set_defaults(fn=cmd_failed)
    s = sub.add_parser("finish")
    s.add_argument("--export", required=True)
    s.add_argument("--manifest", required=True)
    s.add_argument("--dest", required=True)
    s.add_argument("--route")
    s.set_defaults(fn=cmd_finish)
    s = sub.add_parser("compare")
    s.add_argument("left")
    s.add_argument("right")
    s.add_argument("--uuid", action="store_true", help="сравнивать с точностью до UUID")
    s.set_defaults(fn=cmd_compare)
    a = p.parse_args()
    a.fn(a)


if __name__ == "__main__":
    main()
