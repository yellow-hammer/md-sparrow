# -*- coding: utf-8 -*-
"""Эталоны узлов из проекта, который записала 1С:EDT: по одному файлу на класс метамодели.

python harvest.py <src проекта EDT> <mdclass.ecore> <каталог эталонов>

Класс узла берётся из схемы владельца: у реквизита справочника это CatalogAttribute,
у реквизита его табличной части - TabularSectionAttribute. Узел пишется как есть, со
своим отступом верхнего уровня (два пробела); реквизит табличной части сдвигается на
тот же уровень. Табличная часть берётся пустая (ТабличнаяЧасть1), реквизит табличной
части - из ТабличнаяЧасть2 (так их заводит build_seed.py).
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

SRC, ECORE, OUT = sys.argv[1:4]
FEATURES = ("attributes", "tabularSections", "enumValues", "dimensions", "resources", "commands",
            "accountingFlags", "extDimensionAccountingFlags")
XSI_TYPE = "{http://www.w3.org/2001/XMLSchema-instance}type"

classes = {}
for classifier in ET.parse(ECORE).getroot().findall("eClassifiers"):
    if classifier.get(XSI_TYPE) != "ecore:EClass":
        continue
    supers = (classifier.get("eSuperTypes") or "").split()
    supers += [item.get("href") for item in classifier.findall("eSuperTypes")]
    features = {feature.get("name"): feature.get("eType") for feature in classifier.findall("eStructuralFeatures")}
    # Супертипы из других схем признаков состава не объявляют
    classes[classifier.get("name")] = ([s.split("//")[1] for s in supers if s.startswith(("//", "#//"))], features)


def node_class(owner, feature):
    """Класс элементов признака с учётом наследования: CatalogTabularSection.attributes -> TabularSectionAttribute."""
    supers, features = classes[owner]
    if features.get(feature):
        return features[feature].split("//")[1]
    for parent in supers:
        found = node_class(parent, feature)
        if found:
            return found
    return None


def blocks(lines, indent, feature):
    """Участки строк элемента на заданном отступе: (имя, строки)."""
    found = []
    start = None
    for i, line in enumerate(lines):
        if start is None and re.match(rf"^{indent}<{feature}[ >]", line):
            start = i
        elif start is not None and line.startswith(f"{indent}</{feature}>"):
            block = lines[start:i + 1]
            found.append((re.search(r"<name>([^<]*)</name>", "".join(block)).group(1), block))
            start = None
    return found


written = {}
for directory in sorted(os.listdir(SRC)):
    for name in sorted(os.listdir(os.path.join(SRC, directory))):
        mdo = os.path.join(SRC, directory, name, name + ".mdo")
        if not os.path.isfile(mdo):
            continue
        lines = open(mdo, encoding="utf-8").read().replace("\r\n", "\n").split("\n")
        owner = re.search(r"<mdclass:(\w+) ", lines[1]).group(1)
        for feature in FEATURES:
            for node, block in blocks(lines, "  ", feature):
                kind = node_class(owner, feature)
                if feature == "tabularSections":
                    inner = blocks(block, "    ", "attributes")
                    if node == "ТабличнаяЧасть2":
                        written.setdefault(node_class(kind, "attributes"), []).append(
                            (owner, [line[2:] for line in inner[0][1]]))
                        continue
                    assert not inner, f"{owner}: у пустой табличной части есть реквизиты"
                written.setdefault(kind, []).append((owner, block))

os.makedirs(OUT, exist_ok=True)
for kind, variants in sorted(written.items()):
    texts = {"\n".join(re.sub(r'(uuid|typeId|valueTypeId)="[^"]*"', r'\1=""', line) for line in block)
             for _, block in variants}
    if len(texts) > 1:
        print(f"!! {kind}: у владельцев {[owner for owner, _ in variants]} узлы различаются")
    owner, block = variants[0]
    with open(os.path.join(OUT, kind + ".xml"), "w", encoding="utf-8", newline="\n") as target:
        target.write("\n".join(block) + "\n")
    print(f"{kind:45s} <- {', '.join(owner for owner, _ in variants)}")
print(len(written), "классов")
