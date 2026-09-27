# -*- coding: utf-8 -*-
"""Семя для эталонов узлов EDT: голые объекты платформы 8.3.27 (формат 2.20) + по узлу каждого вида.

Узлы минимальные: имя, синоним ru, пустой комментарий и тип (строка 10; у признаков учёта - булево). Идентификаторы - UUID v3 от
имени владельца и узла, чтобы повторный прогон давал те же файлы.

python build_seed.py <каталог голой выгрузки> <каталог семени>
"""
import os
import re
import shutil
import sys
import uuid

SRC, OUT = sys.argv[1], sys.argv[2]
NS = uuid.UUID("6ba7b811-9dad-11d1-80b4-00c04fd430c8")


def u(*parts):
    return str(uuid.uuid3(NS, "edt-golden-nodes|" + "|".join(parts)))


STRING10 = ("<Type><v8:Type>xs:string</v8:Type><v8:StringQualifiers><v8:Length>10</v8:Length>"
            "<v8:AllowedLength>Variable</v8:AllowedLength></v8:StringQualifiers></Type>")
BOOLEAN = "<Type><v8:Type>xs:boolean</v8:Type></Type>"


def synonym(name):
    return f"<Synonym><v8:item><v8:lang>ru</v8:lang><v8:content>{name}</v8:content></v8:item></Synonym>"


def data_node(owner, tag, name, type_block=STRING10):
    return (f'<{tag} uuid="{u(owner, tag, name)}"><Properties><Name>{name}</Name>{synonym(name)}<Comment/>'
            f"{type_block}</Properties></{tag}>")


def enum_value(owner, name):
    return (f'<EnumValue uuid="{u(owner, "EnumValue", name)}"><Properties><Name>{name}</Name>{synonym(name)}'
            f"<Comment/></Properties></EnumValue>")


def command(owner, name):
    return (f'<Command uuid="{u(owner, "Command", name)}"><Properties><Name>{name}</Name>{synonym(name)}<Comment/>'
            "<Group>FormCommandBarImportant</Group><CommandParameterType/><ParameterUseMode>Single</ParameterUseMode>"
            "<ModifiesData>false</ModifiesData><Representation>Auto</Representation><ToolTip/><Picture/><Shortcut/>"
            "<OnMainServerUnavalableBehavior>Auto</OnMainServerUnavalableBehavior></Properties></Command>")


def tabular_section(prefix, owner, name, inner=""):
    gen = "".join(
        f'<xr:GeneratedType name="{prefix}{cat}.{owner}.{name}" category="{cat}">'
        f"<xr:TypeId>{u(owner, name, cat, 'type')}</xr:TypeId><xr:ValueId>{u(owner, name, cat, 'value')}</xr:ValueId>"
        "</xr:GeneratedType>"
        for cat in ("TabularSection", "TabularSectionRow"))
    children = f"<ChildObjects>{inner}</ChildObjects>" if inner else "<ChildObjects/>"
    return (f'<TabularSection uuid="{u(owner, "TabularSection", name)}"><InternalInfo>{gen}</InternalInfo>'
            f"<Properties><Name>{name}</Name>{synonym(name)}<Comment/></Properties>{children}</TabularSection>")


def with_tabular(prefix, owner):
    """Реквизит, пустая табличная часть и табличная часть с реквизитом."""
    return (data_node(owner, "Attribute", "Реквизит1")
            + tabular_section(prefix, owner, "ТабличнаяЧасть1")
            + tabular_section(prefix, owner, "ТабличнаяЧасть2",
                              data_node(owner + ".ТабличнаяЧасть2", "Attribute", "Реквизит1")))


def register(owner):
    return (data_node(owner, "Dimension", "Измерение1") + data_node(owner, "Resource", "Ресурс1")
            + data_node(owner, "Attribute", "Реквизит1") + command(owner, "Команда1"))


# (каталог, имя, тег в составе, узлы)
OWNERS = [
    ("Catalogs", "Справочник1", "Catalog", lambda o: with_tabular("Catalog", o) + command(o, "Команда1")),
    ("Documents", "Документ1", "Document", lambda o: with_tabular("Document", o) + command(o, "Команда1")),
    ("DataProcessors", "Обработка1", "DataProcessor",
     lambda o: with_tabular("DataProcessor", o) + command(o, "Команда1")),
    ("Reports", "Отчет1", "Report", lambda o: with_tabular("Report", o) + command(o, "Команда1")),
    ("ChartsOfCharacteristicTypes", "ПланВидовХарактеристик1", "ChartOfCharacteristicTypes",
     lambda o: with_tabular("ChartOfCharacteristicTypes", o) + command(o, "Команда1")),
    ("ChartsOfCalculationTypes", "ПланВидовРасчета1", "ChartOfCalculationTypes",
     lambda o: with_tabular("ChartOfCalculationTypes", o) + command(o, "Команда1")),
    ("ChartsOfAccounts", "ПланСчетов1", "ChartOfAccounts",
     lambda o: with_tabular("ChartOfAccounts", o)
     + data_node(o, "AccountingFlag", "ПризнакУчета1", BOOLEAN)
     + data_node(o, "ExtDimensionAccountingFlag", "ПризнакУчетаСубконто1", BOOLEAN)
     + command(o, "Команда1")),
    ("ExchangePlans", "ПланОбмена1", "ExchangePlan", lambda o: with_tabular("ExchangePlan", o) + command(o, "Команда1")),
    ("Tasks", "Задача1", "Task", lambda o: with_tabular("Task", o) + command(o, "Команда1")),
    ("BusinessProcesses", "БизнесПроцесс1", "BusinessProcess",
     lambda o: with_tabular("BusinessProcess", o) + command(o, "Команда1")),
    ("Enums", "Перечисление1", "Enum", lambda o: enum_value(o, "Значение1") + command(o, "Команда1")),
    ("DocumentJournals", "ЖурналДокументов1", "DocumentJournal", lambda o: command(o, "Команда1")),
    ("FilterCriteria", "КритерийОтбора1", "FilterCriterion", lambda o: command(o, "Команда1")),
    ("InformationRegisters", "РегистрСведений1", "InformationRegister", register),
    ("AccumulationRegisters", "РегистрНакопления1", "AccumulationRegister", register),
    ("AccountingRegisters", "РегистрБухгалтерии1", "AccountingRegister", register),
    ("CalculationRegisters", "РегистрРасчета1", "CalculationRegister", register),
    ("Sequences", "Последовательность1", "Sequence", lambda o: data_node(o, "Dimension", "Измерение1")),
]

shutil.rmtree(OUT, ignore_errors=True)
os.makedirs(OUT)
shutil.copytree(os.path.join(SRC, "Languages"), os.path.join(OUT, "Languages"))
kept = ["\t\t\t<Language>Русский</Language>"]
for directory, name, tag, nodes in OWNERS:
    text = open(os.path.join(SRC, directory, name + ".xml"), encoding="utf-8-sig").read()
    text, count = re.subn(r"<ChildObjects/>", lambda _: "<ChildObjects>" + nodes(name) + "</ChildObjects>", text, count=1)
    assert count == 1, name
    os.makedirs(os.path.join(OUT, directory), exist_ok=True)
    open(os.path.join(OUT, directory, name + ".xml"), "w", encoding="utf-8").write("﻿" + text)
    kept.append(f"\t\t\t<{tag}>{name}</{tag}>")
configuration = open(os.path.join(SRC, "Configuration.xml"), encoding="utf-8-sig").read()
configuration = re.sub(r"<ChildObjects>.*?</ChildObjects>", lambda _: "<ChildObjects>\n" + "\n".join(kept) + "\n\t\t</ChildObjects>",
                       configuration, count=1, flags=re.S)
open(os.path.join(OUT, "Configuration.xml"), "w", encoding="utf-8").write("﻿" + configuration)
print("семя:", OUT, len(OWNERS), "владельцев")
