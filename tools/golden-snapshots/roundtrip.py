"""Проверка загрузкой: всё, что пишет md-sparrow, платформа принимает и выгружает теми же байтами.

    python roundtrip.py --jar <md-sparrow-*-all.jar> [--bin <каталог ibcmd>] [--work <каталог>] [--out <каталог>]
                        [--scenarios forms,extension,external,all-kinds,dcs]

Формат выгрузки берётся из выгрузки пустой базы этой платформы, md-sparrow пишет файлы в нём же.
Сценарии (по умолчанию все пять, --scenarios выбирает часть):

  forms      объекты десяти видов с формами: cf-form-add и cf-form-compile с основным реквизитом, общая
             форма; у справочника ещё форма с элементами всех видов, которые собирает cf-form-compile,
             после структурной правки (cf-form-item-add, -bind, -rename, -move, -delete), и реквизит
             составного типа (число, строка, дата), записанный cf-md-object-set;
             обычный config import с проверкой, config export
  external   внешние отчёт и обработка с формами: config import --out, config export --file (ibcmd 8.3.23+)
  all-kinds  объекты всех видов формата с дочерними узлами: язык обычным config import, остальное
             config import files --no-check (ibcmd 8.3.20+: голые регистры и планы проверку не проходят)
  extension  расширение к своей конфигурации: свой справочник и заимствованный объект каждого вида,
             который голым проходит config import с проверкой; у заимствованного общего модуля
             cf-md-object-set меняет флаги «Сервер», «Вызов сервера» и «Внешнее соединение»
  dcs        схемы компоновки данных: окружение (объекты, на которые ссылаются схемы) и макеты СКД отчёта,
             обработки и общий макет с правками редактора; обычный config import с проверкой. Вердикт - только
             по файлам СКД, отличия окружения идут в envDiffs. Только форматы 2.12-2.20 (иначе skipped)

Сценарий dcs умеет идти в две фазы - md-sparrow (Java) и ibcmd бывают на разных машинах:

    python roundtrip.py --jar <jar> --prepare <каталог> --format 2.20 [--java <java>] [--java-paths windows]
                        [--dcs-inputs <dcs-inputs.json>]
    python roundtrip.py --prepared <каталог> [--bin <каталог ibcmd>] [--work …] [--out …] [--save-exports]

--prepare пишет дерево конфигурации и manifest.json без платформы и сразу проверяет написанное (строгая
cf-dcs-validate, ссылки схем на объекты окружения, пустая схема против эталона платформы); --prepared загружает
подготовленное без java и --jar.

Основная конфигурация сценария dcs - основная сетка: всё, что пишет редактор схем (таблица DCS_GRID и входы редактора
dcs-inputs.json - тексты конструктора запроса и пакеты мастера, их собирает dcs-inputs.mjs модулями webview
расширения). Строки, которые записал каждый элемент сетки, лежат в манифесте («ожидаемое»): после выгрузки сверка
называет элементы, которые платформа изменила (results.dcs.grid), а вердикт даёт побайтная сверка.

--java-paths windows - md-sparrow запускается java.exe Windows из WSL: пути в параметрах переводятся в пути
Windows. --self-test проверяет сам скрипт без платформы: правила сверки на синтетических деревьях, а с
--prepared (или --jar) - подготовленное дерево, манифест и повторяемость подготовки.

Загруженное платформа проверяет (config check) и применяет к базе (config apply --force), у расширения
с --extension; ibcmd без этих команд их пропускает. Затем идёт второй цикл: первая выгрузка платформы
загружается в новую базу тем же путём, проверяется, применяется и выгружается снова.

Каждый файл, записанный md-sparrow, сверяется с первой выгрузкой байт в байт, а первая выгрузка со
второй; файл, который выгрузка дала сверх сравниваемого, тоже различие (кроме ConfigDumpInfo.xml).
Серии <v8:Type> подряд сравниваются как мультимножества: порядок типов в составном типе платформа
выбирает сама (в сценарии dcs так же и серии <v8:TypeSet>, отдельно от <v8:Type>). Вердикт сценария:
  pass                 записанное md-sparrow совпало с выгрузкой, второй цикл её не изменил
  accepted-normalized  платформа приняла файлы, но выгрузила иначе; второй цикл выгрузку не изменил
  unstable-roundtrip   вторая выгрузка отличается от первой
  rejected             платформа отвергла файлы при загрузке, проверке или применении
  platform             отказ - ограничение версии (см. ниже), skipped - сценарий этой версии не по силам
Если платформа отвергла расширение, тем же путём загружается выгрузка расширения, созданного ею самой.
Отвергнута и она, и все ошибки отказа те же (ibcmd 8.3.20) - сценарий помечается platform: это
ограничение версии, а не различие. Любая другая ошибка - отказ файлов md-sparrow.
Итог - report.txt с различиями и summary.json в --out. Код выхода: 0 - у всех сценариев pass, platform
или skipped, 1 - есть другие вердикты, 2 - сбой самой проверки (и подготовки, и подготовленного дерева).

Рабочий каталог --work стирается перед проверкой, поэтому он должен быть пустым, новым или оставленным
прошлой проверкой (с меткой .roundtrip). На Windows он должен быть коротким: ibcmd не работает с длинными
путями. Каталог --prepare тоже стирается: он должен быть пустым, новым или подготовленным раньше.
"""
import argparse
import datetime
import difflib
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

TIMEOUT = 1800
DUMP_INFO = 'ConfigDumpInfo.xml'
# Метка рабочего каталога: только такой непустой каталог проверка стирает при повторном запуске
WORK_MARKER = '.roundtrip'

# Сценарии в порядке запуска; подготовку без платформы (--prepare) умеет только dcs
SCENARIOS = ('forms', 'extension', 'external', 'all-kinds', 'dcs')
PREPARABLE = ('dcs',)
MANIFEST = 'manifest.json'
MANIFEST_VERSION = 1

# Виды с формой объекта: вид md-sparrow, каталог выгрузки, имя, тип основного реквизита формы
FORM_KINDS = (
    ('CATALOG', 'Catalogs', 'Спр', 'CatalogObject'),
    ('DOCUMENT', 'Documents', 'Док', 'DocumentObject'),
    ('REPORT', 'Reports', 'Отч', 'ReportObject'),
    ('DATA_PROCESSOR', 'DataProcessors', 'Обр', 'DataProcessorObject'),
    ('TASK', 'Tasks', 'Зад', 'TaskObject'),
    ('CHART_OF_ACCOUNTS', 'ChartsOfAccounts', 'ПС', 'ChartOfAccountsObject'),
    ('CHART_OF_CHARACTERISTIC_TYPES', 'ChartsOfCharacteristicTypes', 'ПВХ', 'ChartOfCharacteristicTypesObject'),
    ('CHART_OF_CALCULATION_TYPES', 'ChartsOfCalculationTypes', 'ПВР', 'ChartOfCalculationTypesObject'),
    ('EXCHANGE_PLAN', 'ExchangePlans', 'ПО', 'ExchangePlanObject'),
    ('ENUM', 'Enums', 'Пер', None),
)

ALL_KINDS = (
    'CATALOG ENUM CONSTANT DOCUMENT REPORT DATA_PROCESSOR TASK CHART_OF_ACCOUNTS CHART_OF_CHARACTERISTIC_TYPES '
    'CHART_OF_CALCULATION_TYPES COMMON_MODULE SUBSYSTEM SESSION_PARAMETER EXCHANGE_PLAN COMMON_ATTRIBUTE '
    'COMMON_PICTURE DOCUMENT_NUMERATOR EXTERNAL_DATA_SOURCE ROLE STYLE_ITEM STYLE COMMON_TEMPLATE FILTER_CRITERION '
    'XDTO_PACKAGE WEB_SERVICE HTTP_SERVICE WS_REFERENCE WEB_SOCKET_CLIENT EVENT_SUBSCRIPTION SCHEDULED_JOB '
    'SETTINGS_STORAGE FUNCTIONAL_OPTION FUNCTIONAL_OPTIONS_PARAMETER DEFINED_TYPE BOT PALETTE_COLOR COMMON_COMMAND '
    'COMMAND_GROUP COMMON_FORM SEQUENCE DOCUMENT_JOURNAL INFORMATION_REGISTER ACCUMULATION_REGISTER '
    'ACCOUNTING_REGISTER CALCULATION_REGISTER BUSINESS_PROCESS INTEGRATION_SERVICE').split()

# Виды основной конфигурации сценария extension: голыми они проходят config import с проверкой. Веб- и
# HTTP-сервисы, подписки на события, регламентные задания, функциональные опции и их параметры, общие
# команды, последовательности, журналы, регистры и бизнес-процессы без содержимого её не проходят
BORROW_KINDS = (
    'CATALOG DOCUMENT SUBSYSTEM COMMON_FORM ENUM CONSTANT REPORT DATA_PROCESSOR TASK CHART_OF_ACCOUNTS '
    'CHART_OF_CHARACTERISTIC_TYPES CHART_OF_CALCULATION_TYPES EXCHANGE_PLAN COMMON_MODULE SESSION_PARAMETER '
    'COMMON_ATTRIBUTE COMMON_PICTURE DOCUMENT_NUMERATOR EXTERNAL_DATA_SOURCE ROLE STYLE_ITEM STYLE COMMON_TEMPLATE '
    'FILTER_CRITERION XDTO_PACKAGE WS_REFERENCE SETTINGS_STORAGE DEFINED_TYPE BOT COMMAND_GROUP '
    'INTEGRATION_SERVICE').split()

# Свойства, которые заимствованный объект берёт у оригинала: в основной конфигурации сценария extension
# они меняются в файле, чтобы отличаться от того, что md-sparrow пишет у нового объекта
ORIGINAL_VALUES = (
    ('CommonTemplates', b'<TemplateType>SpreadsheetDocument</TemplateType>', b'<TemplateType>TextDocument</TemplateType>'),
    ('CommonPictures', b'<AvailabilityForAppearance>false</AvailabilityForAppearance>',
     b'<AvailabilityForAppearance>true</AvailabilityForAppearance>'),
)

# Дочерние узлы сценария all-kinds: вид владельца, каталог, имя владельца, операции
NODES = (
    ('CATALOG', 'Catalogs', 'Справочник', (
        ('cf-md-attribute-add', {'name': 'Реквизит'}),
        ('cf-md-tabular-section-add', {'name': 'Товары'}),
        ('cf-md-tabular-attribute-add', {'tabularSection': 'Товары', 'name': 'Номенклатура'}),
        ('cf-md-command-add', {'name': 'Команда'}),
        ('cf-form-add', {'name': 'ФормаЭлемента'}))),
    ('DOCUMENT', 'Documents', 'Документ', (
        ('cf-md-attribute-add', {'name': 'Реквизит'}),
        ('cf-md-tabular-section-add', {'name': 'Товары'}),
        ('cf-form-add', {'name': 'ФормаДокумента'}))),
    ('ENUM', 'Enums', 'Перечисление', (
        ('cf-md-enum-value-add', {'name': 'Значение'}),)),
    ('INFORMATION_REGISTER', 'InformationRegisters', 'РегистрСведений', (
        ('cf-md-dimension-add', {'name': 'Измерение'}),
        ('cf-md-resource-add', {'name': 'Ресурс'}),
        ('cf-md-attribute-add', {'name': 'Реквизит'}))),
    ('ACCUMULATION_REGISTER', 'AccumulationRegisters', 'РегистрНакопления', (
        ('cf-md-dimension-add', {'name': 'Измерение'}),
        ('cf-md-resource-add', {'name': 'Ресурс'}))),
    ('CHART_OF_ACCOUNTS', 'ChartsOfAccounts', 'ПланСчетов', (
        ('cf-md-accounting-flag-add', {'name': 'Количественный'}),
        ('cf-md-ext-dimension-accounting-flag-add', {'name': 'Суммовой'}))),
    ('DATA_PROCESSOR', 'DataProcessors', 'Обработка', (
        ('cf-md-attribute-add', {'name': 'Реквизит'}),
        ('cf-md-tabular-section-add', {'name': 'Строки'}),
        ('cf-form-add', {'name': 'Форма'}))),
)

# Форма с элементами сценария forms: все виды элементов cf-form-compile, таблица с колонкой
FORM_ITEMS = {
    'items': [
        {'pages': 'Страницы', 'items': [
            {'page': 'Основное', 'title': 'Основное', 'items': [
                {'group': 'Шапка', 'direction': 'horizontal', 'items': [
                    {'input': 'Наименование', 'dataPath': 'Объект.Description'},
                    {'check': 'ПометкаУдаления', 'dataPath': 'Объект.DeletionMark'},
                ]},
                {'label': 'Надпись', 'title': 'Состав'},
            ]},
        ]},
        {'table': 'Товары', 'dataPath': 'Объект.Товары', 'items': [
            {'input': 'ТоварыНоменклатура', 'dataPath': 'Объект.Товары.Номенклатура'},
        ]},
    ],
}

# Составной тип реквизита сценария forms: квалификаторы всех трёх видов
COMPOSITE_TYPE = {
    'types': ['xs:decimal', 'xs:string', 'xs:dateTime'],
    'numberQualifiers': {'digits': '15', 'fractionDigits': '2', 'allowedSign': 'ANY'},
    'stringQualifiers': {'length': '50', 'allowedLength': 'VARIABLE'},
    'dateQualifiers': {'dateFractions': 'DATE_TIME'},
}

# ---------- сценарий dcs ----------
# Форматы, в которых md-sparrow создаёт макет СКД (cf-dcs-template-add): канон снят в 2.20, 2.12-2.19 -
# проекция описания макета; на 2.21 и до 2.12 операция отказывает, поэтому сценарий там skipped
DCS_FORMATS = ((2, 12), (2, 20))

# Окружение схем: объекты, на которые ссылаются схемы и запросы. Пишет только md-sparrow и только виды, которые
# голыми проходят config import с проверкой (как BORROW_KINDS): регистров нет, им нужны регистратор и ресурс.
# Семя ssl31 не берём: объекты БСП тянут модули, формы, подсистемы и роли, и проверку они не пройдут
DCS_ENV = (
    ('CATALOG', 'Catalogs', 'Валюты', ()),
    ('ENUM', 'Enums', 'Статусы', (
        ('cf-md-enum-value-add', {'name': 'Новый'}),
        ('cf-md-enum-value-add', {'name': 'Закрыт'}))),
    ('DOCUMENT', 'Documents', 'ЗаказПокупателя', (
        ('cf-md-attribute-add', {'name': 'Валюта'}),
        ('cf-md-attribute-add', {'name': 'Сумма'}),
        ('cf-md-attribute-add', {'name': 'Статус'}))),
    ('CHART_OF_CHARACTERISTIC_TYPES', 'ChartsOfCharacteristicTypes', 'ВидыСвойств', ()),
    ('DEFINED_TYPE', 'DefinedTypes', 'ТипКода', ()),
    ('DATA_PROCESSOR', 'DataProcessors', 'Обработка1', ()),
    ('REPORT', 'Reports', 'Отчет1', ()),
)

# Типы реквизитов окружения (cf-md-object-set): ссылки на объекты окружения и число с квалификаторами - их
# раскрывают запросы схем
DCS_ENV_TYPES = (
    ('Documents', 'ЗаказПокупателя', {
        'Валюта': {'types': ['cfg:CatalogRef.Валюты']},
        'Сумма': {'types': ['xs:decimal'],
                  'numberQualifiers': {'digits': '15', 'fractionDigits': '2', 'allowedSign': 'NONNEGATIVE'}},
        'Статус': {'types': ['cfg:EnumRef.Статусы']},
    }),
)

# Пути шагов схем: @ключ в параметре шага заменяется путём внутри конфигурации сценария
DCS_PATHS = {
    'conf': 'Configuration.xml',
    'report': 'Reports/Отчет1.xml',
    'report2': 'Reports/Отчет2.xml',
    'dp': 'DataProcessors/Обработка1.xml',
    'main': 'Reports/Отчет1/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml',
    'queries': 'Reports/Отчет2/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml',
    'common': 'CommonTemplates/ОбщийМакет1/Ext/Template.xml',
    'empty': 'CommonTemplates/ПустаяСхема/Ext/Template.xml',
    'dps': 'DataProcessors/Обработка1/Templates/СхемаОбработки/Ext/Template.xml',
}

# Текст запроса в каноне конструктора: табуляции, КАК у каждого поля и источника, без перевода строки в конце
DCS_QUERY_ORDERS = '\n'.join([
    'ВЫБРАТЬ',
    '\tЗаказ.Ссылка КАК Заказ,',
    '\tЗаказ.Дата КАК Дата,',
    '\tЗаказ.Валюта КАК Валюта,',
    '\tЗаказ.Сумма КАК Сумма,',
    '\tЗаказ.Статус КАК Статус',
    'ИЗ',
    '\tДокумент.ЗаказПокупателя КАК Заказ',
    'ГДЕ',
    '\tЗаказ.Дата МЕЖДУ &Начало И &Конец',
])

# Второй набор основной схемы: параметр &Заказы - параметр связи наборов, в параметры схемы он не объявляется
DCS_QUERY_PAYMENTS = '\n'.join([
    'ВЫБРАТЬ',
    '\tЗаказ.Ссылка КАК Заказ,',
    '\tЗаказ.Сумма КАК СуммаОплаты,',
    '\tЗаказ.Дата КАК ДатаОплаты',
    'ИЗ',
    '\tДокумент.ЗаказПокупателя КАК Заказ',
    'ГДЕ',
    '\tЗаказ.Ссылка В (&Заказы)',
])

# Третий набор основной схемы (элемент связи-параметр): два параметра связей наборов, как &Заказы у второго набора
DCS_QUERY_FILTERED = '\n'.join([
    'ВЫБРАТЬ',
    '\tЗаказ.Ссылка КАК Заказ,',
    '\tЗаказ.Валюта КАК Валюта,',
    '\tЗаказ.Статус КАК Статус',
    'ИЗ',
    '\tДокумент.ЗаказПокупателя КАК Заказ',
    'ГДЕ',
    '\tЗаказ.Валюта В (&ВалютыОтбора)',
    '\tИ Заказ.Статус В (&СтатусыОтбора)',
])

# Входы сетки от редактора схем расширения (тексты конструктора запроса и пакеты мастера настроек): файл собирает
# dcs-inputs.mjs модулями webview, --dcs-inputs подставляет другой
DCS_INPUTS = Path(__file__).resolve().parent / 'dcs-inputs.json'

# 47 лексем типа диаграммы (v8ui:ChartType) в порядке перечисления XSD data-ui; самотест сверяет список с XSD,
# с DcsSettingsEdit.CHART_TYPES и со списком мастера настроек расширения (chartTypes входов)
CHART_TYPES = (
    'Line', 'Step', 'StackedLine', 'Area', 'StackedArea', 'NormalizedArea',
    'Column', 'StackedColumn', 'NormalizedColumn', 'Column3D', 'StackedColumn3D', 'NormalizedColumn3D',
    'Bar', 'StackedBar', 'NormalizedBar', 'Bar3D', 'StackedBar3D', 'NormalizedBar3D',
    'Pie', 'Pie3D', 'Stock', 'OpenHighLowClose', 'BarGraph', 'CeilGraph', 'TapeGraph', 'PyramidGraph',
    'Waterfall', 'WireframeSurface', 'Honeycomb', 'Surface', 'ConvexSurface', 'ConcaveSurface',
    'ShadedSurface', 'RadarLine', 'RadarArea', 'RadarStackedLine', 'RadarStackedArea',
    'RadarNormalizedArea', 'Gauge', 'Funnel', 'Funnel3D', 'NormalizedFunnel', 'NormalizedFunnel3D',
    'Scatter', 'Bubble', 'Donut', 'Donut3D')


def repo_root(script=None):
    """Корень репозитория md-sparrow: скрипт лежит в tools/golden-snapshots. На машину с ibcmd едут только
    roundtrip.py и prepared/, и копия может лежать неглубоко (/work/roundtrip.py, /tmp/roundtrip.py), без третьего
    родителя. Тогда корнем считается корень файловой системы: файлов репозитория там нет, и сверки по ним пропускаются
    сами («рядом нет»). script - для самотеста."""
    parents = Path(script or __file__).resolve().parents
    return parents[2] if len(parents) > 2 else parents[-1]


REPO_ROOT = repo_root()

# Эталон пустой схемы - выгрузка платформы 8.3.27 (submodule samples-1c-platform): макет, который md-sparrow создаёт
# без правок, обязан совпасть с ним байт в байт. Содержимое Template.xml версии формата не несёт - эталон один
EMPTY_SCHEMA_ETALON = (REPO_ROOT / 'fixtures' / 'samples-1c-platform' / 'snapshots' / '2.20'
                       / 'cf-dcs' / 'CommonTemplates' / 'ОбщийМакет1' / 'Ext' / 'Template.xml')


def step(op, target='@main', payload=None, **params):
    """Шаг сетки: операция md-sparrow над файлом target (@ключ DCS_PATHS); payload - словарь или текст."""
    out = {'op': op, 'objectXml': target}
    out.update(params)
    if payload is not None:
        out['payload'] = payload
    return out


def sv(op, payload, variant='Основной', target='@main'):
    """Шаг правки варианта настроек."""
    return step(op, target, payload, variant=variant)


def node(*steps):
    """Путь к узлу структуры: пары (список, номер) от корня настроек."""
    return [{'list': lst, 'index': i} for lst, i in steps]


def output_value(name, value, variant='Основной', path=None, target='@main'):
    payload = {'section': 'output', 'parameter': name, 'value': value}
    if path is not None:
        payload['path'] = path
    return sv('cf-dcs-set-parameter-value', payload, variant, target)


def grid(gid, group, what, steps, batch=True):
    """Элемент сетки: id, группа возможностей редактора (group: 1 - наборы, поля, связи и параметры, 2 - варианты
    настроек, 3 - макеты, 4 - типы и значения, конструктор запроса и мастер настроек), что проверяет, шаги. Шаги
    элемента идут одним пакетом md-sparrow (как пишут мастер и конструктор), batch=False - по одному: операции над
    макетом целиком (создание и удаление) видят диск."""
    return {'id': gid, 'group': group, 'what': what, 'steps': list(steps), 'batch': batch}


def t_string(length, allowed='VARIABLE'):
    return {'types': ['xs:string'], 'stringQualifiers': {'length': str(length), 'allowedLength': allowed}}


def t_decimal(digits, fraction, sign='ANY'):
    return {'types': ['xs:decimal'], 'numberQualifiers': {'digits': str(digits), 'fractionDigits': str(fraction),
                                                          'allowedSign': sign}}


def t_date(fractions):
    return {'types': ['xs:dateTime'], 'dateQualifiers': {'dateFractions': fractions}}


EMPTY_DATE = {'type': 'xs:dateTime', 'text': '0001-01-01T00:00:00'}


def group_field(field, group_type='Items', addition='None'):
    """Поле группировки полной пятёркой (так его требует cf-dcs-set-group-fields)."""
    return {'field': field, 'groupType': group_type, 'periodAdditionType': addition,
            'periodAdditionBegin': EMPTY_DATE, 'periodAdditionEnd': EMPTY_DATE}


# Основная сетка сценария dcs: всё, что пишет редактор схем, - операции md-sparrow по таблице, тексты
# руками не правятся. Схемы: основная схема Отчет1 (наборы, поля, связи, вычисляемые поля, ресурсы, параметры с
# типами и значениями, варианты настроек, мастер), основная схема Отчет2 (тексты конструктора запроса), ОбщийМакет1
# (47 типов диаграмм), макет обработки (набор-объект) и ПустаяСхема (эталон платформы, без правок). Платформа должна
# выгрузить всё теми же байтами (pass); строки, которые записал каждый элемент, - в манифесте («ожидаемое»), по ним
# сверка после выгрузки называет элементы, которые платформа изменила
DCS_GRID = (
    # ---------- макеты ----------
    grid('макеты', 3, 'создание макетов СКД: основная схема отчёта (makeMain), общий макет, макет обработки без '
                      'makeMain, пустая схема - эталон платформы', [
        step('cf-dcs-template-add', '@report', name='ОсновнаяСхемаКомпоновкиДанных', makeMain=True),
        step('cf-dcs-template-add', '@conf', name='ОбщийМакет1'),
        step('cf-dcs-template-add', '@dp', name='СхемаОбработки'),
        step('cf-dcs-template-add', '@conf', name='ПустаяСхема')], batch=False),
    grid('удаление-макета', 3, 'создание и удаление лишнего макета: владелец возвращается к прежним байтам', [
        step('cf-dcs-template-add', '@report', name='Лишний'),
        step('cf-dcs-template-remove', '@report', name='Лишний')], batch=False),
    # ---------- наборы, поля, связи и параметры ----------
    grid('набор-заказы', 1, 'набор-запрос «Заказы» пакетом конструктора: текст, недостающие параметры, автозаполнение', [
        step('cf-dcs-add-data-set', name='Заказы', type='query', dataSource='ИсточникДанных1'),
        step('cf-dcs-set-query', payload=DCS_QUERY_ORDERS, tag='Заказы'),
        step('cf-dcs-add-parameter', payload={'name': 'Начало'}),
        step('cf-dcs-add-parameter', payload={'name': 'Конец'}),
        step('cf-dcs-autofill-fields', dataSet='Заказы')]),
    grid('набор-оплаты', 1, 'второй набор-запрос: имя через переименование (каскад), поле с заголовком руками, '
                            'автозаполнение остального, autoFillFields=false; добавление и удаление набора', [
        step('cf-dcs-add-data-set', name='ЛишнийНабор', type='query', dataSource='ИсточникДанных1'),
        step('cf-dcs-remove-data-set', name='ЛишнийНабор'),
        step('cf-dcs-add-data-set', name='ОплатыЧерновик', type='query', dataSource='ИсточникДанных1'),
        step('cf-dcs-rename-data-set', name='ОплатыЧерновик', newName='Оплаты'),
        step('cf-dcs-set-query', payload=DCS_QUERY_PAYMENTS, tag='Оплаты'),
        step('cf-dcs-add-field', payload={'dataPath': 'ДатаОплаты', 'field': 'ДатаОплаты', 'title': 'Дата оплаты'},
             dataSet='Оплаты'),
        step('cf-dcs-autofill-fields', dataSet='Оплаты'),
        step('cf-dcs-set-data-set-props', payload={'autoFillFields': False}, name='Оплаты')]),
    grid('поля', 1, 'свойства полей набора: заголовок, ограничения использования поля и реквизитов, роли (измерение, '
                    'период, остаток, многострочное выражение вида счёта), добавление и удаление поля', [
        step('cf-dcs-set-field', payload={'title': 'Дата заказа', 'role': {'periodNumber': 1, 'periodType': 'Main'}},
             dataSet='Заказы', dataPath='Дата'),
        step('cf-dcs-set-field', payload={'role': {'dimension': True}, 'useRestriction': {'condition': True}},
             dataSet='Заказы', dataPath='Валюта'),
        step('cf-dcs-set-field', payload={
            'title': 'Сумма заказа', 'attributeUseRestriction': {'group': True, 'order': True},
            'role': {'balance': True, 'balanceType': 'ClosingBalance'}}, dataSet='Заказы', dataPath='Сумма'),
        step('cf-dcs-set-field', payload={'role': {'accountTypeExpression': 'ВЫБОР\n\tКОГДА Статус = ЗНАЧЕНИЕ('
                                                                         'Перечисление.Статусы.Новый)\n\tТОГДА 1\nКОНЕЦ'}},
             dataSet='Заказы', dataPath='Статус'),
        step('cf-dcs-add-field', payload={'dataPath': 'Лишнее', 'field': 'Лишнее'}, dataSet='Заказы'),
        step('cf-dcs-remove-field', dataSet='Заказы', dataPath='Лишнее')]),
    grid('связи', 1, 'связь наборов с параметром, списком и многострочными выражениями условия и начала; правка и '
                     'удаление связи по номеру', [
        step('cf-dcs-add-link', payload={
            'sourceDataSet': 'Заказы', 'destinationDataSet': 'Оплаты', 'sourceExpression': 'Заказ',
            'destinationExpression': 'Заказ', 'parameter': 'Заказы', 'parameterListAllowed': True,
            'linkConditionExpression': 'Сумма > 0\n\tИ НЕ Статус ЕСТЬ NULL'}),
        # required=true платформа не хранит ни в одном сочетании элементов связи, md-sparrow в нём отказывает: сетка
        # его не пишет
        step('cf-dcs-set-link', payload={'startExpression': 'ЕСТЬNULL(Заказ,\n\tНЕОПРЕДЕЛЕНО)'}, index=0),
        step('cf-dcs-add-link', payload={
            'sourceDataSet': 'Заказы', 'destinationDataSet': 'Оплаты', 'sourceExpression': 'Дата',
            'destinationExpression': 'ДатаОплаты'}),
        step('cf-dcs-remove-link', index=1)]),
    # parameterListAllowed=false у связи с parameter платформа дописывает сама, и md-sparrow пишет его так же:
    # при add-link с parameter без флага и при set-link, задающем parameter связи без флага. Выгрузка должна совпасть
    # байт в байт
    grid('связи-параметр', 1, 'связи с параметром без флага списка: add-link с parameter и set-link, задающий '
                              'parameter, - md-sparrow пишет parameterListAllowed=false, как платформа; третий '
                              'набор-запрос с двумя параметрами связей', [
        step('cf-dcs-add-data-set', name='ЗаказыПоОтбору', type='query', dataSource='ИсточникДанных1'),
        step('cf-dcs-set-query', payload=DCS_QUERY_FILTERED, tag='ЗаказыПоОтбору'),
        step('cf-dcs-autofill-fields', dataSet='ЗаказыПоОтбору'),
        step('cf-dcs-add-link', payload={
            'sourceDataSet': 'Заказы', 'destinationDataSet': 'ЗаказыПоОтбору', 'sourceExpression': 'Валюта',
            'destinationExpression': 'Валюта', 'parameter': 'ВалютыОтбора'}),
        step('cf-dcs-add-link', payload={
            'sourceDataSet': 'Заказы', 'destinationDataSet': 'ЗаказыПоОтбору', 'sourceExpression': 'Статус',
            'destinationExpression': 'Статус'}),
        step('cf-dcs-set-link', payload={'parameter': 'СтатусыОтбора'}, index=2)]),
    grid('вычисляемые-поля', 1, 'вычисляемое поле с заголовком и многострочным выражением; добавление и удаление', [
        step('cf-dcs-add-calculated-field', payload={'dataPath': 'СуммаСНДС', 'expression': 'Сумма\n\t* 1.2',
                                                     'title': 'Сумма с НДС'}),
        step('cf-dcs-add-calculated-field', payload={'dataPath': 'Лишнее', 'expression': '1'}),
        step('cf-dcs-remove-calculated-field', dataPath='Лишнее')]),
    grid('ресурсы', 1, 'ресурсы: по всем группировкам и по списку группировок, правка групп по номеру, удаление', [
        step('cf-dcs-add-total-field', payload={'dataPath': 'Сумма', 'expression': 'Сумма(Сумма)'}),
        step('cf-dcs-add-total-field', payload={'dataPath': 'СуммаСНДС', 'expression': 'Сумма(СуммаСНДС)',
                                                'groups': ['Валюта']}),
        step('cf-dcs-set-total-field', payload={'groups': ['Статус', 'Валюта']}, index=1),
        step('cf-dcs-add-total-field', payload={'dataPath': 'Лишнее', 'expression': 'Количество(Заказ)'}),
        step('cf-dcs-remove-total-field', index=2)]),
    grid('параметры', 1, 'параметры: заголовок, многострочное выражение, «Всегда», ограничение использования '
                         'true/false, переименование и удаление', [
        step('cf-dcs-add-parameter', payload={'name': 'Период', 'title': 'Период отчёта', 'use': 'Always',
                                              'useRestriction': False}),
        step('cf-dcs-add-parameter', payload={'name': 'ДатаОтчета', 'title': 'Дата отчёта',
                                              'expression': 'КОНЕЦПЕРИОДА(&Конец,\n\tДЕНЬ)', 'useRestriction': True}),
        step('cf-dcs-add-parameter', payload={'name': 'Черновик'}),
        step('cf-dcs-set-parameter', payload={'newName': 'Переименованный', 'title': 'Переименованный параметр'},
             name='Черновик'),
        step('cf-dcs-add-parameter', payload={'name': 'Лишний'}),
        step('cf-dcs-remove-parameter', name='Лишний')]),
    # ---------- типы и значения ----------
    grid('типы-полей', 4, 'типы полей набора и вычисляемого поля: ссылки, число и дата с квалификаторами, составной', [
        step('cf-dcs-set-field', payload={'title': 'Заказ покупателя',
                                          'valueType': {'types': ['cfg:DocumentRef.ЗаказПокупателя']}},
             dataSet='Заказы', dataPath='Заказ'),
        step('cf-dcs-set-field', payload={'valueType': t_date('DATE_TIME')}, dataSet='Заказы', dataPath='Дата'),
        step('cf-dcs-set-field', payload={'valueType': {'types': ['cfg:CatalogRef.Валюты']}}, dataSet='Заказы',
             dataPath='Валюта'),
        step('cf-dcs-set-field', payload={'valueType': t_decimal(15, 2, 'NONNEGATIVE')}, dataSet='Заказы',
             dataPath='Сумма'),
        step('cf-dcs-set-field', payload={'valueType': {'types': ['cfg:EnumRef.Статусы']}}, dataSet='Заказы',
             dataPath='Статус'),
        step('cf-dcs-set-calculated-field', payload={'valueType': {
            'types': ['xs:decimal', 'xs:string'],
            'numberQualifiers': {'digits': '15', 'fractionDigits': '2', 'allowedSign': 'ANY'},
            'stringQualifiers': {'length': '10', 'allowedLength': 'VARIABLE'}}}, dataPath='СуммаСНДС')]),
    grid('типы-параметров', 4, 'типы параметров: примитивы с квалификаторами (строка 0/50, число 15,2, дата), '
                               'ссылки трёх видов, набор вида, любая ссылка, характеристика, определяемый тип по '
                               'имени, стандартный период, UUID, составной тип', [
        step('cf-dcs-set-parameter', payload={'valueType': t_date('DATE')}, name='Начало'),
        step('cf-dcs-set-parameter', payload={'valueType': t_date('DATE_TIME')}, name='Конец'),
        step('cf-dcs-set-parameter', payload={'valueType': {'types': ['v8:StandardPeriod']}}, name='Период'),
        step('cf-dcs-set-parameter', payload={'valueType': t_date('DATE')}, name='ДатаОтчета'),
        step('cf-dcs-add-parameter', payload={'name': 'СтрокаБезОграничения', 'valueType': t_string(0)}),
        step('cf-dcs-add-parameter', payload={'name': 'СтрокаФиксированная', 'valueType': t_string(50, 'FIXED')}),
        step('cf-dcs-add-parameter', payload={'name': 'ЧислоНеотрицательное',
                                              'valueType': t_decimal(15, 2, 'NONNEGATIVE')}),
        step('cf-dcs-add-parameter', payload={'name': 'Статус', 'valueType': {'types': ['cfg:EnumRef.Статусы']}}),
        step('cf-dcs-add-parameter', payload={'name': 'ДокументЗаказ',
                                              'valueType': {'types': ['cfg:DocumentRef.ЗаказПокупателя']}}),
        step('cf-dcs-add-parameter', payload={'name': 'Валюты', 'valueType': {'types': ['cfg:CatalogRef.Валюты']}}),
        step('cf-dcs-add-parameter', payload={'name': 'ЛюбойСправочник', 'valueType': {'typeSets': ['cfg:CatalogRef']}}),
        step('cf-dcs-add-parameter', payload={'name': 'ЛюбаяСсылка', 'valueType': {'typeSets': ['cfg:AnyIBRef']}}),
        # характеристика в формате - набор типов (<v8:TypeSet>), а не тип
        step('cf-dcs-add-parameter', payload={'name': 'Свойство',
                                              'valueType': {'typeSets': ['cfg:Characteristic.ВидыСвойств']}}),
        step('cf-dcs-add-parameter', payload={'name': 'Код', 'valueType': {'definedTypes': ['ТипКода']}}),
        step('cf-dcs-add-parameter', payload={'name': 'Идентификатор', 'valueType': {'types': ['v8:UUID']}}),
        step('cf-dcs-add-parameter', payload={'name': 'Флаг', 'valueType': {'types': ['xs:boolean']}}),
        # составной тип: в выгрузке порядок типов выбирает платформа (сверка сравнивает серии типов мультимножеством)
        step('cf-dcs-add-parameter', payload={'name': 'Отбор', 'valueType': {
            'types': ['xs:string', 'cfg:CatalogRef.Валюты', 'xs:decimal'], 'typeSets': ['cfg:EnumRef'],
            'stringQualifiers': {'length': '50', 'allowedLength': 'VARIABLE'},
            'numberQualifiers': {'digits': '10', 'fractionDigits': '0', 'allowedSign': 'ANY'}}})]),
    grid('значения-параметров', 4, 'значения параметров: дата, стандартный период, значение конфигуратора, xsi:nil, '
                                   'список значений при valueListAllowed (повторяющиеся value без представлений), '
                                   'многострочная строка, пустая строка у UUID, число; флаги availableAsField=false и '
                                   'denyIncompleteValues=true', [
        step('cf-dcs-set-parameter', payload={'value': {'type': 'xs:dateTime', 'text': '2026-01-01T00:00:00'}},
             name='Начало'),
        step('cf-dcs-set-parameter', payload={'value': {'type': 'v8:StandardPeriod', 'period': {'variant': 'ThisMonth'}}},
             name='Период'),
        step('cf-dcs-set-parameter', payload={'value': {'type': 'dcscor:DesignTimeValue',
                                                        'text': 'Перечисление.Статусы.Новый'}}, name='Статус'),
        step('cf-dcs-set-parameter', payload={'value': {'nil': True}}, name='ДокументЗаказ'),
        # представлений элементов платформа у параметра схемы не хранит: md-sparrow в них отказывает, список пишется
        # повторяющимися value
        step('cf-dcs-set-parameter', payload={'valueListAllowed': True, 'value': {
            'type': 'v8:ValueListType', 'list': {'items': [
                {'value': {'type': 'xs:string', 'text': 'USD'}},
                {'value': {'type': 'xs:string', 'text': 'EUR'}}]}}}, name='СтрокаБезОграничения'),
        step('cf-dcs-set-parameter', payload={'value': {'type': 'xs:string',
                                                        'text': 'первая строка\nвторая строка\n\tс табуляцией  '}},
             name='СтрокаФиксированная'),
        # пустая строка - «значение не задано» у параметра другого типа (так в корпусе ssl31)
        step('cf-dcs-set-parameter', payload={'value': {'type': 'xs:string', 'text': ''}}, name='Идентификатор'),
        step('cf-dcs-set-parameter', payload={'value': {'type': 'xs:decimal', 'decoded': 10.5}},
             name='ЧислоНеотрицательное'),
        step('cf-dcs-set-parameter', payload={'value': {'type': 'xs:boolean', 'decoded': True},
                                              'availableAsField': False, 'denyIncompleteValues': True}, name='Флаг')]),
    # ---------- вариант «Основной» ----------
    grid('поля-отбор-порядок', 2, 'выбранные поля (с заголовком, папка, авто), отбор (сравнение, группа ИЛИ, '
                                  'многострочное правое значение, значение конфигуратора), порядок', [
        sv('cf-dcs-set-selection', {'path': [], 'items': [
            {'kind': 'field', 'field': 'Заказ'},
            {'kind': 'field', 'field': 'Дата', 'title': 'Дата заказа'},
            {'kind': 'folder', 'title': 'Суммы', 'placement': 'Auto', 'items': [
                {'kind': 'field', 'field': 'Сумма'}, {'kind': 'field', 'field': 'СуммаСНДС'}]},
            {'kind': 'auto'}]}),
        sv('cf-dcs-set-filter', {'path': [], 'items': [
            {'kind': 'comparison', 'left': 'Сумма', 'comparisonType': 'Greater',
             'right': [{'type': 'xs:decimal', 'decoded': 0}]},
            {'kind': 'group', 'groupType': 'OrGroup', 'items': [
                {'kind': 'comparison', 'left': 'Статус', 'comparisonType': 'Equal',
                 'right': [{'type': 'dcscor:DesignTimeValue', 'text': 'Перечисление.Статусы.Новый'}]},
                {'kind': 'comparison', 'left': 'Валюта', 'comparisonType': 'Filled'}]},
            {'kind': 'comparison', 'left': 'Валюта.Наименование', 'comparisonType': 'Contains',
             'right': [{'type': 'xs:string', 'text': 'первая строка\nвторая строка'}],
             'presentation': 'Наименование валюты содержит'}]}),
        sv('cf-dcs-set-order', {'path': [], 'items': [
            {'kind': 'field', 'field': 'Дата', 'orderType': 'Desc'}, {'kind': 'auto'}]})]),
    grid('параметры-варианта', 2, 'параметры данных (use и значение; use=true - отсутствием элемента, как его хранит '
                                  'платформа) и параметры вывода, записанные не по порядку рангов: многострочный '
                                  'Заголовок, строка, перечисления', [
        sv('cf-dcs-set-parameter-value', {'section': 'data', 'parameter': 'Начало', 'use': True,
                                          'value': {'type': 'xs:dateTime', 'text': '2026-01-01T00:00:00'}}),
        sv('cf-dcs-set-parameter-value', {'section': 'data', 'parameter': 'Период',
                                          'value': {'type': 'v8:StandardPeriod', 'period': {'variant': 'LastMonth'}}}),
        sv('cf-dcs-set-parameter-value', {'section': 'data', 'parameter': 'Статус', 'use': False,
                                          'value': {'type': 'dcscor:DesignTimeValue',
                                                    'text': 'Перечисление.Статусы.Закрыт'}}),
        output_value('Заголовок', {'type': 'v8:LocalStringType', 'decoded': 'Заказы\nпо валютам'}),
        output_value('РасположениеИтогов', {'type': 'dcscor:DataCompositionTotalPlacement', 'text': 'End'}),
        output_value('ВыводитьОтбор', {'type': 'dcsset:DataCompositionTextOutputType', 'text': 'DontOutput'}),
        output_value('ВыводитьЗаголовок', {'type': 'dcsset:DataCompositionTextOutputType', 'text': 'Output'}),
        output_value('МакетОформления', 'БезОформления')]),
    grid('структура', 2, 'структура: группировка с иерархией, вложенная группа «Только иерархия», детальные записи, '
                         'таблица со строками, колонками и вложенной группой строки, диаграмма с точками, сериями и '
                         'своим типом; перестановка, поля группировки пятёркой, удаление узла, порядок и параметр '
                         'вывода у группировки', [
        sv('cf-dcs-add-structure-item', {'parentPath': [], 'list': 'structure', 'kind': 'group',
                                         'groupFields': [{'field': 'Валюта', 'groupType': 'Hierarchy'}]}),
        sv('cf-dcs-add-structure-item', {'parentPath': node(('structure', 0)), 'list': 'structure', 'kind': 'group',
                                         'groupFields': [{'field': 'Статус', 'groupType': 'HierarchyOnly'}]}),
        sv('cf-dcs-add-structure-item', {'parentPath': node(('structure', 0), ('structure', 0)), 'list': 'structure',
                                         'kind': 'group'}),
        sv('cf-dcs-add-structure-item', {'parentPath': [], 'list': 'structure', 'kind': 'table'}),
        sv('cf-dcs-add-structure-item', {'parentPath': node(('structure', 1)), 'list': 'rows',
                                         'groupFields': [{'field': 'Валюта'}]}),
        sv('cf-dcs-add-structure-item', {'parentPath': node(('structure', 1)), 'list': 'columns',
                                         'groupFields': [{'field': 'Статус'}]}),
        sv('cf-dcs-add-structure-item', {'parentPath': node(('structure', 1), ('rows', 0)), 'list': 'structure',
                                         'kind': 'group', 'groupFields': [{'field': 'Дата'}]}),
        sv('cf-dcs-add-structure-item', {'parentPath': [], 'list': 'structure', 'kind': 'chart'}),
        sv('cf-dcs-add-structure-item', {'parentPath': node(('structure', 2)), 'list': 'points',
                                         'groupFields': [{'field': 'Валюта'}]}),
        sv('cf-dcs-add-structure-item', {'parentPath': node(('structure', 2)), 'list': 'series',
                                         'groupFields': [{'field': 'Статус'}]}),
        output_value('ТипДиаграммы', {'type': 'v8ui:ChartType', 'text': 'Column3D'}, path=node(('structure', 2))),
        sv('cf-dcs-move-structure-item', {'path': node(('structure', 1)), 'newIndex': 2}),
        sv('cf-dcs-set-group-fields', {'path': node(('structure', 0)), 'items': [
            group_field('Валюта', 'Hierarchy'), group_field('Дата', 'Items', 'Month')]}),
        sv('cf-dcs-set-order', {'path': node(('structure', 0)), 'items': [
            {'kind': 'field', 'field': 'Валюта', 'orderType': 'Asc'}]}),
        output_value('РасположениеИтогов', {'type': 'dcscor:DataCompositionTotalPlacement', 'text': 'Begin'},
                     path=node(('structure', 0))),
        sv('cf-dcs-add-structure-item', {'parentPath': [], 'list': 'structure', 'kind': 'group',
                                         'groupFields': [{'field': 'Заказ'}]}),
        sv('cf-dcs-remove-structure-item', {'path': node(('structure', 3))})]),
    grid('оформление', 2, 'условное оформление: поле, условие, цвет, многострочный текст, представление, области '
                          'использования; замена элемента и удаление', [
        sv('cf-dcs-add-appearance-item', {'path': [], 'item': {
            'fields': [{'field': 'Сумма'}],
            'filter': [{'kind': 'comparison', 'left': 'Сумма', 'comparisonType': 'Less',
                        'right': [{'type': 'xs:decimal', 'decoded': 0}]}],
            'appearance': [{'parameter': 'ЦветТекста', 'value': [{'type': 'v8ui:Color', 'text': 'web:Red'}]},
                           {'parameter': 'Текст', 'value': [{'type': 'v8:LocalStringType',
                                                             'decoded': 'отрицательная\nсумма'}]}],
            'presentation': 'Отрицательные суммы', 'useIn': {'header': 'DontUse'}}}),
        sv('cf-dcs-add-appearance-item', {'path': [], 'item': {
            'fields': [{'field': 'СуммаСНДС'}], 'filter': [],
            'appearance': [{'parameter': 'Формат', 'value': [{'type': 'v8:LocalStringType', 'decoded': 'ЧДЦ=2'}]}]}}),
        sv('cf-dcs-set-appearance-item', {'path': [], 'index': 1, 'item': {
            'fields': [{'field': 'СуммаСНДС'}, {'field': 'Сумма'}], 'filter': [],
            'appearance': [{'parameter': 'Формат', 'value': [{'type': 'v8:LocalStringType', 'decoded': 'ЧДЦ=2; ЧН=-'}]}],
            'presentation': 'Формат сумм'}}),
        sv('cf-dcs-add-appearance-item', {'path': [], 'item': {
            'fields': [], 'filter': [],
            'appearance': [{'parameter': 'ЦветФона', 'value': [{'type': 'v8ui:Color', 'text': 'web:Yellow'}]}]}}),
        sv('cf-dcs-remove-appearance-item', {'path': [], 'index': 2})]),
    grid('варианты', 2, 'варианты: новый, переименование и представление, удаление', [
        step('cf-dcs-add-settings-variant', payload={'presentation': 'Черновик'}, name='Черновик'),
        step('cf-dcs-set-settings-variant-props', payload={'newName': 'Сводка', 'presentation': 'Сводный отчёт'},
             name='Черновик'),
        step('cf-dcs-add-settings-variant', name='Удаляемый'),
        step('cf-dcs-remove-settings-variant', name='Удаляемый')]),
    # ---------- общий макет: набор и 47 типов диаграмм ----------
    grid('общий-макет', 3, 'набор-запрос общего макета: текст и автозаполнение отдельными операциями', [
        step('cf-dcs-add-data-set', '@common', name='Валюты', type='query', dataSource='ИсточникДанных1'),
        step('cf-dcs-set-query', '@common', '\n'.join(['ВЫБРАТЬ', '\tВалюты.Ссылка КАК Валюта', 'ИЗ',
                                                       '\tСправочник.Валюты КАК Валюты']), tag='Валюты'),
        step('cf-dcs-autofill-fields', '@common', dataSet='Валюты')], batch=False),
    grid('типы-диаграмм', 4, 'вариант «Все типы диаграмм»: 47 диаграмм, у каждой своя лексема ТипДиаграммы', [
        step('cf-dcs-add-settings-variant', '@common', {'presentation': 'Все типы диаграмм'}, name='ВсеТипыДиаграмм')]
        + [sv('cf-dcs-add-structure-item', {'parentPath': [], 'list': 'structure', 'kind': 'chart'},
              'ВсеТипыДиаграмм', '@common') for _ in CHART_TYPES]
        + [output_value('ТипДиаграммы', {'type': 'v8ui:ChartType', 'text': lexeme}, 'ВсеТипыДиаграмм',
                        node(('structure', i)), '@common') for i, lexeme in enumerate(CHART_TYPES)]),
    # ---------- макет обработки: набор-объект ----------
    grid('набор-объект', 1, 'набор-объект в макете обработки и поле набора', [
        step('cf-dcs-add-data-set', '@dps', name='Обработка1', type='object', dataSource='ИсточникДанных1'),
        step('cf-dcs-add-field', '@dps', {'dataPath': 'Реквизит', 'field': 'Реквизит', 'title': 'Реквизит объекта'},
             dataSet='Обработка1')]),
)

# Операции, которые пишут элементы сетки из входов редактора: пакет мастера - только мутации вариантов (те, что
# расширение пропускает в пакетном вызове мастера)
WIZARD_OPS = ('cf-dcs-add-settings-variant', 'cf-dcs-remove-structure-item', 'cf-dcs-set-selection', 'cf-dcs-set-order',
              'cf-dcs-set-group-fields', 'cf-dcs-add-structure-item', 'cf-dcs-set-parameter-value')

# Имена объектов окружения в текстах запросов входов: только они есть в конфигурации сценария
QUERY_TABLE = re.compile(r'(?<![\w.&])(Справочник|Документ|Перечисление|ПланВидовХарактеристик)\.(\w+)')


def load_dcs_inputs(path):
    """Входы сетки от редактора: тексты конструктора запроса и пакеты мастера (dcs-inputs.json)."""
    if not Path(path).is_file():
        raise Failure('нет входов сетки dcs %s: соберите их dcs-inputs.mjs (см. README) или задайте --dcs-inputs'
                      % path)
    try:
        data = json.loads(Path(path).read_text(encoding='utf-8'))
    except ValueError as e:
        raise Failure('входы сетки dcs %s не читаются: %s' % (path, e))
    problems = dcs_inputs_problems(data)
    if problems:
        raise Failure('входы сетки dcs %s: %s' % (path, '; '.join(problems[:10])))
    return data


def dcs_inputs_problems(data):
    """Что не так со входами: строение, имена наборов, ссылки текстов вне окружения, операции мастера."""
    if not isinstance(data, dict) or not isinstance(data.get('queries'), list) or not isinstance(data.get('wizard'), list):
        return ['не объект с queries[] и wizard[]']
    problems = []
    env = {(kind, name) for kind, _folder, name, _ops in DCS_ENV}
    ru = {'Справочник': 'CATALOG', 'Документ': 'DOCUMENT', 'Перечисление': 'ENUM',
          'ПланВидовХарактеристик': 'CHART_OF_CHARACTERISTIC_TYPES'}
    names = [q.get('dataSet') for q in data['queries'] if isinstance(q, dict)]
    if len(names) != len(data['queries']) or len({str(n).lower() for n in names}) != len(names):
        problems.append('имена наборов запросов повторяются (без учёта регистра) или не заданы')
    for q in data['queries']:
        if not isinstance(q, dict) or not isinstance(q.get('text'), str) or not q['text'].strip() \
                or not isinstance(q.get('parameters'), list):
            problems.append('запрос без текста или списка параметров: %r' % (q.get('dataSet') if isinstance(q, dict) else q))
            continue
        for kind, name in QUERY_TABLE.findall(q['text']):
            if (ru[kind], name) not in env:
                problems.append('запрос %s ссылается на %s.%s - его нет в окружении сценария' % (q['dataSet'], kind, name))
    variants = [w.get('variant') for w in data['wizard'] if isinstance(w, dict)]
    if len({str(v).lower() for v in variants}) != len(data['wizard']):
        problems.append('имена вариантов мастера повторяются (без учёта регистра) или не заданы')
    for w in data['wizard']:
        ops = w.get('operations') if isinstance(w, dict) else None
        if not isinstance(ops, list) or not ops or ops[0].get('op') != 'cf-dcs-add-settings-variant' \
                or ops[0].get('name') != w.get('variant'):
            problems.append('пакет мастера %r не начинается новым вариантом своего имени' % w.get('variant'))
            continue
        for o in ops[1:]:
            if o.get('op') not in WIZARD_OPS or o.get('variant') != w['variant']:
                problems.append('пакет мастера %s: операция %s вне каталога или не своего варианта' % (
                    w['variant'], o.get('op')))
    return problems


def dcs_grid(inputs):
    """Сетка целиком: таблица DCS_GRID и элементы из входов редактора (конструктор запроса - схема Отчет2, мастер -
    варианты основной схемы «В новый вариант…»).

    Пакет конструктора - как после ОК окна: cf-dcs-set-query, cf-dcs-add-parameter на каждый &-параметр текста, ещё не
    объявленный в схеме (без учёта регистра; тип не задаётся, как у редактора), и cf-dcs-autofill-fields у набора с
    автозаполнением. У последнего запроса набор с autoFillFields=false: конструктор автозаполнение не зовёт.
    """
    entries = list(DCS_GRID)
    entries.append(grid('отчет-запросов', 3, 'второй отчёт с основной схемой - под тексты конструктора запроса', [
        step('add-md-object', configurationXml='@conf', type='REPORT', name='Отчет2'),
        step('cf-dcs-template-add', '@report2', name='ОсновнаяСхемаКомпоновкиДанных', makeMain=True)]))
    declared = set()
    queries = inputs['queries']
    for i, q in enumerate(queries):
        autofill = i < len(queries) - 1
        steps = [step('cf-dcs-add-data-set', '@queries', name=q['dataSet'], type='query', dataSource='ИсточникДанных1')]
        if not autofill:
            steps.append(step('cf-dcs-set-data-set-props', '@queries', {'autoFillFields': False}, name=q['dataSet']))
        steps.append(step('cf-dcs-set-query', '@queries', q['text'], tag=q['dataSet']))
        for name in q['parameters']:
            if name.lower() not in declared:
                declared.add(name.lower())
                steps.append(step('cf-dcs-add-parameter', '@queries', {'name': name}))
        if autofill:
            steps.append(step('cf-dcs-autofill-fields', '@queries', dataSet=q['dataSet']))
        entries.append(grid('запрос-' + q['dataSet'], 4, 'конструктор запроса: ' + q['what']
                            + ('' if autofill else '; набор без автозаполнения'), steps))
    for w in inputs['wizard']:
        steps = [dict(o, objectXml='@main') for o in w['operations']]
        entries.append(grid('мастер-' + w['variant'], 4, 'мастер настроек «В новый вариант…»: ' + w['what'], steps))
    return entries


# Корень XSD формата (submodule namespace-forest)
XSD_ROOT = REPO_ROOT / 'resources' / 'namespace-forest'

# Ранги параметров вывода - копия таблицы DcsSettingsEdit.OUTPUT_PARAMETER_RANKS (самотест сверяет её с исходником
# md-sparrow, если он рядом). Имена одной строки - пары «=»: их взаимный порядок платформы не известен, и вместе на
# одном узле они не хранятся
OUTPUT_RANKS = (
    ('МакетОформления',), ('КоличествоЗаписей',), ('ПроцентЗаписей',), ('ТипМакета',), ('РасположениеИтогов',),
    ('РасположениеПолейГруппировки',), ('РасположениеГруппировки',), ('РасположениеРеквизитов',),
    ('РасположениеРесурсов',), ('ГоризонтальноеРасположениеОбщихИтогов', 'РасположениеОбщихИтогов'),
    ('ВертикальноеРасположениеОбщихИтогов',), ('ТипЗаголовкаПолей',), ('ТипДиаграммы',), ('ВыводитьЗаголовок',),
    ('Заголовок',), ('ВыводитьПараметрыДанных',), ('ВыводитьОтбор',), ('АвтоПозицияРесурсов',), ('ФиксацияСлева',),
    ('ВариантИспользованияГруппировки', 'ФиксацияСверху'))
OUTPUT_NAMES = tuple(name for group in OUTPUT_RANKS for name in group)

# Параметры вывода, которые платформа в корне настроек не хранит (загрузка молча отбрасывает) - копия
# DcsSettingsEdit.ROOT_FORBIDDEN_OUTPUT_PARAMETERS (самотест сверяет): md-sparrow в корне в них отказывает
ROOT_FORBIDDEN_OUTPUT = ('КоличествоЗаписей', 'ПроцентЗаписей', 'ТипМакета', 'РасположениеОбщихИтогов',
                         'ВариантИспользованияГруппировки')

# Параметры вывода, которые платформа у группировки структуры на любой глубине не хранит (загрузка молча
# отбрасывает) - копия DcsSettingsEdit.GROUP_FORBIDDEN_OUTPUT_PARAMETERS (самотест сверяет): md-sparrow у группировки
# структуры в них отказывает. У групп таблицы и диаграммы запреты свои (ниже)
GROUP_FORBIDDEN_OUTPUT = ('ФиксацияСверху', 'ФиксацияСлева', 'ВыводитьПараметрыДанных', 'РасположениеОбщихИтогов',
                          'ГоризонтальноеРасположениеОбщихИтогов')

# Параметры вывода, которые платформа не хранит у таблицы, группы таблицы (строк, колонок и вложенных в них групп),
# диаграммы и группы диаграммы (точек, серий и вложенных в них групп) - копии DcsSettingsEdit.TABLE_, TABLE_GROUP_,
# CHART_ и CHART_GROUP_FORBIDDEN_OUTPUT_PARAMETERS (самотест сверяет): md-sparrow на этих уровнях в них отказывает.
# Вложенные группы ограничены так же, как верхние
TABLE_FORBIDDEN_OUTPUT = ('ФиксацияСверху', 'ФиксацияСлева', 'ВариантИспользованияГруппировки', 'ВыводитьОтбор',
                          'ВыводитьПараметрыДанных', 'ТипДиаграммы', 'РасположениеОбщихИтогов', 'ТипМакета',
                          'ПроцентЗаписей', 'КоличествоЗаписей')
TABLE_GROUP_FORBIDDEN_OUTPUT = ('ФиксацияСверху', 'ФиксацияСлева', 'ВыводитьПараметрыДанных', 'ТипДиаграммы',
                                'ВертикальноеРасположениеОбщихИтогов', 'ГоризонтальноеРасположениеОбщихИтогов',
                                'ТипМакета')
CHART_FORBIDDEN_OUTPUT = tuple(n for n in OUTPUT_NAMES if n not in ('ТипДиаграммы', 'ВыводитьЗаголовок', 'Заголовок'))
CHART_GROUP_FORBIDDEN_OUTPUT = tuple(n for n in OUTPUT_NAMES if n not in ('КоличествоЗаписей', 'ПроцентЗаписей'))

# Запрет параметров вывода по уровню узла - как DcsSettingsEdit.OutputLevel: корень, группировка структуры, таблица,
# группа таблицы, диаграмма, группа диаграммы
LEVEL_FORBIDDEN_OUTPUT = {'root': ROOT_FORBIDDEN_OUTPUT, 'group': GROUP_FORBIDDEN_OUTPUT, 'table': TABLE_FORBIDDEN_OUTPUT,
                          'table-group': TABLE_GROUP_FORBIDDEN_OUTPUT, 'chart': CHART_FORBIDDEN_OUTPUT,
                          'chart-group': CHART_GROUP_FORBIDDEN_OUTPUT}


def output_level(kind, lists):
    """Уровень узла настроек, как DcsSettingsEdit.OutputLevel.of: kind - 'settings', 'table', 'chart' или 'group',
    lists - списки шагов пути (structure, rows, columns, points, series). У группы: шаг points или series - группа
    диаграммы, rows или columns - группа таблицы, одни structure - группировка структуры."""
    if kind in ('settings', 'table', 'chart'):
        return {'settings': 'root', 'table': 'table', 'chart': 'chart'}[kind]
    if any(lst in ('points', 'series') for lst in lists):
        return 'chart-group'
    return 'table-group' if any(lst in ('rows', 'columns') for lst in lists) else 'group'


# Пары «=» таблицы рангов (ГоризонтальноеРасположениеОбщихИтогов / РасположениеОбщихИтогов и
# ВариантИспользованияГруппировки / ФиксацияСверху) вместе не хранятся ни на одном уровне: в корне и у группировки в
# каждой паре одно имя из ROOT_FORBIDDEN_OUTPUT, другое из GROUP_FORBIDDEN_OUTPUT; у таблицы из пар остаётся
# ГоризонтальноеРасположениеОбщихИтогов, у групп таблицы - РасположениеОбщихИтогов и ВариантИспользованияГруппировки, у
# диаграммы и её групп - ни одного. Поэтому строгий порядок пар не нужен; правило пар по всем шести уровням сверяет
# самотест

# Уровни словами и имена их запретов в DcsSettingsEdit - для сверки с исходником md-sparrow
LEVEL_TITLES = {'root': 'в корне настроек', 'group': 'у группировки структуры', 'table': 'у таблицы',
                'table-group': 'у групп таблицы', 'chart': 'у диаграммы', 'chart-group': 'у групп диаграммы'}
LEVEL_CONSTANTS = {'table': 'TABLE_FORBIDDEN_OUTPUT_PARAMETERS', 'table-group': 'TABLE_GROUP_FORBIDDEN_OUTPUT_PARAMETERS',
                   'chart': 'CHART_FORBIDDEN_OUTPUT_PARAMETERS', 'chart-group': 'CHART_GROUP_FORBIDDEN_OUTPUT_PARAMETERS'}


# Файлы СКД: содержимое макета с корнем DataCompositionSchema, его описание и в объекте-владельце - свойство
# MainDataCompositionSchema и строки состава макетов. Всё прочее - окружение
TEMPLATE_CONTENT = re.compile(r'^(?:(.+)/Templates/([^/]+)|CommonTemplates/([^/]+))/Ext/Template\.xml$')
DCS_ROOT = re.compile(rb'<DataCompositionSchema[\s/>]')
MAIN_DCS = re.compile(r'[ \t]*<MainDataCompositionSchema(?:\s*/>|>[^<]*</MainDataCompositionSchema>)[ \t]*\r?\n?')

# Ссылки схем на объекты конфигурации: тип с объявлением префикса на самом элементе (так пишут платформа и
# md-sparrow), значение конфигуратора «Перечисление.Статусы.Новый» и UUID сгенерированного типа
CURRENT_CONFIG = 'http://v8.1c.ru/8.1/data/enterprise/current-config'
REF_TYPE = re.compile(r'<v8:Type(?:Set)?\s+xmlns:(\w+)="' + re.escape(CURRENT_CONFIG)
                      + r'"\s*>\1:(\w+)\.([^<\s]+)</v8:Type(?:Set)?>')
TYPE_ID = re.compile(r'<v8:TypeId>([0-9a-fA-F-]{36})</v8:TypeId>')
DESIGN_TIME = re.compile(r'xsi:type="dcscor:DesignTimeValue"\s*>([^<]+)<')
CHILD_OBJECT = re.compile(r'<(\w+)>([^<]+)</\1>')
REF_KINDS = {
    'CatalogRef': 'Catalog', 'DocumentRef': 'Document', 'EnumRef': 'Enum',
    'ChartOfCharacteristicTypesRef': 'ChartOfCharacteristicTypes', 'ChartOfAccountsRef': 'ChartOfAccounts',
    'ChartOfCalculationTypesRef': 'ChartOfCalculationTypes', 'ExchangePlanRef': 'ExchangePlan',
    'BusinessProcessRef': 'BusinessProcess', 'TaskRef': 'Task',
    'Characteristic': 'ChartOfCharacteristicTypes', 'DefinedType': 'DefinedType',
}
RU_KINDS = {
    'Справочник': ('Catalog', 'Catalogs'), 'Документ': ('Document', 'Documents'), 'Перечисление': ('Enum', 'Enums'),
    'ПланВидовХарактеристик': ('ChartOfCharacteristicTypes', 'ChartsOfCharacteristicTypes'),
    'ПланСчетов': ('ChartOfAccounts', 'ChartsOfAccounts'),
    'ПланВидовРасчета': ('ChartOfCalculationTypes', 'ChartsOfCalculationTypes'),
    'ПланОбмена': ('ExchangePlan', 'ExchangePlans'), 'БизнесПроцесс': ('BusinessProcess', 'BusinessProcesses'),
    'Задача': ('Task', 'Tasks'),
}

LOG = []


class Failure(Exception):
    """Сбой самой проверки, а не различие в файлах."""


def log(*parts):
    line = ' '.join(str(p) for p in parts)
    LOG.append(line)
    print(line, flush=True)


def run(cmd, env):
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', errors='replace',
                           env=env, timeout=TIMEOUT)
    except subprocess.TimeoutExpired:
        return 124, 'таймаут %d с' % TIMEOUT
    return r.returncode, ((r.stdout or '') + (r.stderr or '')).strip()


def win_path(path):
    """Путь WSL на диске Windows (/mnt/c/…) -> путь Windows (C:\\…): так его понимает java.exe Windows."""
    m = re.match(r'^/mnt/([a-zA-Z])(/.*)?$', str(path))
    if not m:
        raise Failure('путь %s не на диске Windows (/mnt/<диск>/…): java.exe его не увидит' % path)
    return m.group(1).upper() + ':' + (m.group(2) or '/').replace('/', '\\')


def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


class Roundtrip:

    def __init__(self, jar, ibcmd, work, env, java='java', java_paths='native', roots=()):
        self.jar, self.ibcmd, self.work, self.env = jar, ibcmd, work, env
        self.java, self.java_paths = java, java_paths
        # Каталоги, пути внутри которых в режиме windows переводятся для java.exe
        self.roots = [str(work)] + [str(r) for r in roots]
        self.data_mode = None
        self.version = None
        self.results = {}
        self.calls = 0
        self.save_exports = None
        self.platform = None
        # журнал сверки dcs: самотест подменяет его, чтобы имитация платформы не засоряла вывод
        self.say = log
        # входы основной сетки dcs от редактора схем (--dcs-inputs): нужны только при подготовке
        self.dcs_inputs = DCS_INPUTS
        if ibcmd:
            help_text = run([ibcmd, 'help', 'config'], env)[1] + run([ibcmd, 'help', 'infobase'], env)[1]
            self.has_no_check = '--base-dir=' in help_text and '--no-check' in help_text
            self.has_extension_create = '--name-prefix=' in help_text
            self.has_check_apply = bool(re.search(r'\bcheck\b', help_text) and re.search(r'\bapply\b', help_text))
            self.platform = run([ibcmd, '--version'], env)[1].splitlines()[0].strip()

    def set_format(self, fmt):
        self.format = fmt
        self.version = 'V' + fmt.replace('.', '_')

    # ---------- md-sparrow ----------
    def java_prefix(self):
        if self.java_paths == 'windows':
            # java.exe Windows из WSL: пути - Windows, кодировка имён файлов и вывода - UTF-8 явно, иначе
            # кириллица в путях и ответах придёт в кодировке системы
            return [self.java, '-Dfile.encoding=UTF-8', '-Dsun.jnu.encoding=UTF-8', '-Dstdout.encoding=UTF-8',
                    '-Dstderr.encoding=UTF-8', '-jar', win_path(self.jar)]
        # вывод md-sparrow читается как UTF-8: иначе на Windows он придёт в кодировке системы
        return [self.java, '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-jar', str(self.jar)]

    def java_cmd(self, command, params_path):
        path = win_path(params_path) if self.java_paths == 'windows' else str(params_path)
        return self.java_prefix() + [command, '--params', path]

    def host_params(self, params):
        """Параметры для md-sparrow: в режиме windows пути внутри рабочих каталогов - пути Windows."""
        if self.java_paths != 'windows':
            return params

        def convert(value):
            if isinstance(value, str) and any(value == r or value.startswith(r + '/') for r in self.roots):
                return win_path(value)
            if isinstance(value, list):
                return [{k: convert(v) for k, v in x.items()} if isinstance(x, dict) else x for x in value]
            return value
        return {k: convert(v) for k, v in params.items()}

    def ms_call(self, command, params):
        params = dict(params)
        params.setdefault('schemaVersion', self.version)
        path = self.work / 'params.json'
        path.write_text(json.dumps(self.host_params(params), ensure_ascii=False), encoding='utf-8')
        self.calls += 1
        rc, out = run(self.java_cmd(command, path), self.env)
        if rc != 0:
            raise Failure('md-sparrow %s: код %s: %s' % (params['op'], rc, out[-600:]))
        return out

    def ms(self, params):
        return self.ms_call('apply-mutation', params)

    def ms_read(self, params):
        """Чтение через read-json: JSON-ответ md-sparrow."""
        out = self.ms_call('read-json', params)
        # вывод склеен с потоком ошибок, куда JVM пишет, например, про JAVA_TOOL_OPTIONS: ответ - первый объект
        return json.JSONDecoder().raw_decode(out, out.index('{'))[0]

    def edit_object(self, obj, change):
        """Правка свойств объекта: cf-md-object-get, изменение словаря, cf-md-object-set."""
        dto = self.ms_read({'op': 'cf-md-object-get', 'objectXml': obj})
        change(dto)
        self.ms({'op': 'cf-md-object-set', 'objectXml': obj, 'payloadJson': json.dumps(dto, ensure_ascii=False)})

    # ---------- ibcmd ----------
    def create(self, name):
        base = self.work / 'ib' / name
        shutil.rmtree(base, ignore_errors=True)
        (base / 'db').mkdir(parents=True)
        (base / 'data').mkdir()
        if self.data_mode != 'no':
            rc, out = run([self.ibcmd, 'infobase', 'create', '--db-path=%s' % (base / 'db'),
                           '--data=%s' % (base / 'data'), '--locale=ru'], self.env)
            if rc == 0:
                self.data_mode = 'yes'
                return name
            if self.data_mode == 'yes':
                raise Failure('infobase create: %s' % out[-600:])
        # у ibcmd 8.3.19 нет --data
        rc, out = run([self.ibcmd, 'infobase', 'create', '--db-path=%s' % (base / 'db'), '--locale=ru'], self.env)
        if rc != 0:
            raise Failure('infobase create: %s' % out[-600:])
        self.data_mode = 'no'
        return name

    def ib(self, name, words, args):
        base = self.work / 'ib' / name
        db = ['--db-path=%s' % (base / 'db')]
        if self.data_mode == 'yes':
            db.append('--data=%s' % (base / 'data'))
        return run([self.ibcmd, 'infobase', *words, *db, *args], self.env)

    def ib_ok(self, name, words, args, what):
        rc, out = self.ib(name, words, args)
        if rc != 0:
            log('  платформа отвергла (%s, код %s):' % (what, rc))
            for line in out.splitlines()[-25:]:
                log('    ' + line[:300])
        return rc == 0

    # ---------- сверка ----------
    def compare(self, title, ours, theirs, what=('md-sparrow', 'платформа')):
        same, diffs = 0, []
        # во втором цикле «записанное» - первая выгрузка платформы, и сведения о выгрузке есть с обеих сторон
        ours_files = {p.relative_to(ours).as_posix() for p in ours.rglob('*') if p.is_file() and p.name != DUMP_INFO}
        theirs_files = {p.relative_to(theirs).as_posix() for p in theirs.rglob('*')
                        if p.is_file() and p.name != DUMP_INFO}
        for rel in sorted(ours_files | theirs_files):
            a, b = ours / rel, theirs / rel
            if rel not in theirs_files:
                diffs.append(rel)
                log('  НЕТ В ВЫГРУЗКЕ', rel)
                continue
            if rel not in ours_files:
                diffs.append(rel)
                log('  ТОЛЬКО В ВЫГРУЗКЕ', rel)
                continue
            da, db = a.read_bytes(), b.read_bytes()
            if da == db:
                same += 1
                continue
            if type_series(da) == type_series(db):
                same += 1
                log('  совпало с точностью до порядка типов', rel)
                continue
            diffs.append(rel)
            log('  РАЗЛИЧИЕ', rel)
            log_difference(da, db, what)
        log('  итог %s: совпало %d, различий %d' % (title, same, len(diffs)))
        return same, diffs

    def record(self, scenario, status, same=0, diffs=(), note='', unstable=(), **extra):
        self.results[scenario] = {'status': status, 'same': same, 'diffs': list(diffs), 'note': note,
                                  'unstable': list(unstable)}
        # дополнительные поля сценария (у dcs - envDiffs и typeOrder)
        self.results[scenario].update(extra)

    def check_apply(self, name, extension=None):
        """config check и config apply --force: принимает ли платформа загруженное как конфигурацию базы."""
        if not self.has_check_apply:
            return True
        ext = ['--extension=%s' % extension] if extension else []
        return (self.ib_ok(name, ['config', 'check'], ext, 'config check')
                and self.ib_ok(name, ['config', 'apply'], [*ext, '--force'], 'config apply'))

    def verdict(self, scenario, ours, first, load_again):
        """Сверка записанного с первой выгрузкой и второй цикл.

        :param load_again: функция (каталог первой выгрузки) -> каталог второй выгрузки или None, если
            платформа отвергла собственную выгрузку
        """
        same, diffs = self.compare(scenario, ours, first)
        log('  второй цикл %s: загрузка первой выгрузки платформы' % scenario)
        second = load_again(first)
        if second is None:
            self.record(scenario, 'unstable-roundtrip', same, diffs, note='платформа отвергла свою выгрузку')
            return
        _, unstable = self.compare(scenario + ' (второй цикл)', first, second, ('выгрузка 1', 'выгрузка 2'))
        if unstable:
            status = 'unstable-roundtrip'
        elif diffs:
            status = 'accepted-normalized'
        else:
            status = 'pass'
        self.record(scenario, status, same, diffs, unstable=unstable)

    # ---------- сценарии ----------
    def detect_format(self):
        self.create('probe')
        out = self.work / 'probe-out'
        if not self.ib_ok('probe', ['config', 'export'], [str(out)], 'выгрузка пустой базы'):
            raise Failure('не удалось выгрузить пустую базу')
        text = (out / 'Configuration.xml').read_text(encoding='utf-8-sig')
        m = re.search(r'<MetaDataObject\b[^>]*\bversion="(\d+\.\d+)"', text)
        if not m:
            raise Failure('в выгрузке пустой базы нет версии формата')
        self.set_format(m.group(1))
        log('Платформа %s, формат %s' % (self.platform, self.format))

    def forms(self):
        log('--- forms: объекты с формами, config import с проверкой')
        cf = self.work / 'forms'
        self.ms({'op': 'init-empty-cf', 'targetCfRoot': str(cf)})
        conf = str(cf / 'Configuration.xml')
        for kind, folder, name, main in FORM_KINDS:
            self.ms({'op': 'add-md-object', 'configurationXml': conf, 'type': kind, 'name': name})
            obj = str(cf / folder / (name + '.xml'))
            self.ms({'op': 'cf-form-add', 'objectXml': obj, 'name': 'Форма'})
            if main:
                payload = {'mainAttribute': {'name': 'Объект', 'type': 'cfg:%s.%s' % (main, name)}}
                self.ms({'op': 'cf-form-compile', 'objectXml': obj, 'name': 'ФормаОбъекта',
                         'payloadJson': json.dumps(payload, ensure_ascii=False)})
        self.ms({'op': 'add-md-object', 'configurationXml': conf, 'type': 'COMMON_FORM', 'name': 'ОбщаяФорма'})
        catalog = str(cf / 'Catalogs' / 'Спр.xml')
        self.ms({'op': 'cf-md-attribute-add', 'objectXml': catalog, 'name': 'Составной'})
        self.ms({'op': 'cf-md-tabular-section-add', 'objectXml': catalog, 'name': 'Товары'})
        self.ms({'op': 'cf-md-tabular-attribute-add', 'objectXml': catalog, 'tabularSection': 'Товары',
                 'name': 'Номенклатура'})

        def composite(dto):
            attribute = next(a for a in dto['attributes'] if a['name'] == 'Составной')
            attribute['type'] = COMPOSITE_TYPE
        self.edit_object(catalog, composite)
        self.ms({'op': 'cf-form-compile', 'objectXml': catalog, 'name': 'ФормаСЭлементами',
                 'payloadJson': json.dumps({'mainAttribute': {'name': 'Объект', 'type': 'cfg:CatalogObject.Спр'},
                                            **FORM_ITEMS}, ensure_ascii=False)})
        self.edit_form_items(str(cf / 'Catalogs' / 'Спр' / 'Forms' / 'ФормаСЭлементами' / 'Ext' / 'Form.xml'))
        # параметры - на своей форме: так видно, что из правки состава платформа дополняет
        self.ms({'op': 'cf-form-compile', 'objectXml': catalog, 'name': 'ФормаСПараметрами',
                 'payloadJson': json.dumps({'mainAttribute': {'name': 'Объект', 'type': 'cfg:CatalogObject.Спр'}},
                                           ensure_ascii=False)})
        self.edit_form_composition(cf / 'Catalogs' / 'Спр' / 'Forms' / 'ФормаСЭлементами',
                                   str(cf / 'Catalogs' / 'Спр' / 'Forms' / 'ФормаСПараметрами' / 'Ext' / 'Form.xml'))
        self.form_events(cf)
        out = self.load_cf('forms', cf)
        if out is None:
            self.record('forms', 'rejected')
            return
        self.verdict('forms', cf, out, lambda first: self.load_cf('forms-2', first))

    def edit_form_items(self, form):
        """Структурная правка элементов формы: добавление, привязка, переименование, перенос, удаление."""
        def ids():
            content = self.ms_read({'op': 'cf-form-content-get', 'formXml': form})
            out = {}

            def walk(items):
                for item in items:
                    out[item['name']] = item['id']
                    walk(item.get('items') or [])
            walk(content['items'])
            return out
        found = ids()
        code = self.ms({'op': 'cf-form-item-add', 'formXml': form, 'parentId': found['Шапка'],
                        'beforeId': found['ПометкаУдаления'],
                        'payloadJson': json.dumps({'input': 'Код'}, ensure_ascii=False)})
        # ответ - номер нового элемента; поток ошибок JVM склеен с ним
        code = next(line.strip() for line in code.splitlines() if line.strip().isdigit())
        self.ms({'op': 'cf-form-item-bind', 'formXml': form, 'itemId': code, 'dataPath': 'Объект.Code'})
        self.ms({'op': 'cf-form-item-rename', 'formXml': form, 'itemId': found['Надпись'], 'newName': 'Пояснение'})
        self.ms({'op': 'cf-form-item-move', 'formXml': form, 'itemId': found['Надпись'], 'parentId': found['Шапка']})
        self.ms({'op': 'cf-form-item-delete', 'formXml': form, 'itemId': found['ПометкаУдаления']})

    def form_events(self, cf):
        """Обработчик открытия у формы объекта каждого вида: в 2.15-2.17 с ним платформа дописывает свойства."""
        for _, folder, name, main in FORM_KINDS:
            if not main:
                continue
            form_dir = cf / folder / name / 'Forms' / 'ФормаОбъекта'
            self.ms({'op': 'cf-form-event-set', 'formXml': str(form_dir / 'Ext' / 'Form.xml'),
                     'event': 'OnOpen', 'handler': 'ПриОткрытии'})
            module = form_dir / 'Ext' / 'Form' / 'Module.bsl'
            module.parent.mkdir(parents=True, exist_ok=True)
            with open(module, 'a', encoding='utf-8-sig', newline='\r\n') as out:
                out.write('\n'.join(['&НаКлиенте', 'Процедура ПриОткрытии(Отказ)', 'КонецПроцедуры', '']))

    def edit_form_composition(self, form_dir, parameters_form):
        """Правка состава формы: реквизиты с колонками и таблица на них, команда, обработчики; параметры - на своей форме."""
        form = str(form_dir / 'Ext' / 'Form.xml')

        def payload(value):
            return json.dumps(value, ensure_ascii=False)

        def answer(text):
            # ответ - номер записи; поток ошибок JVM склеен с ним
            return next(line.strip() for line in text.splitlines() if line.strip().isdigit())
        decimal = {'types': ['xs:decimal'],
                   'numberQualifiers': {'digits': '10', 'fractionDigits': '2', 'allowedSign': 'ANY'}}
        string = {'types': ['xs:string'], 'stringQualifiers': {'length': '100', 'allowedLength': 'VARIABLE'}}
        self.ms({'op': 'cf-form-attribute-add', 'formXml': form, 'payloadJson': payload({
            'name': 'ТоварыФормы', 'title': 'Товары', 'type': {'types': ['v8:ValueTable']},
            'columns': [{'name': 'Цена', 'type': decimal}, {'name': 'Комментарий', 'title': 'Комм.', 'type': string}]})})
        self.ms({'op': 'cf-form-attribute-add', 'formXml': form,
                 'payloadJson': payload({'name': 'ТоварыФормы.Количество', 'type': decimal})})
        self.ms({'op': 'cf-form-attribute-add', 'formXml': form,
                 'payloadJson': payload({'name': 'Флаг', 'type': {'types': ['xs:boolean']}, 'savedData': True})})
        self.ms({'op': 'cf-form-attribute-add', 'formXml': form,
                 'payloadJson': payload({'name': 'Варианты', 'type': {'types': ['v8:ValueListType']}})})
        self.ms({'op': 'cf-form-attribute-add', 'formXml': form,
                 'payloadJson': payload({'name': 'Документ', 'type': {'types': ['mxl:SpreadsheetDocument']}})})
        self.ms({'op': 'cf-form-attribute-rename', 'formXml': form, 'oldName': 'ТоварыФормы.Комментарий',
                 'newName': 'Примечание'})
        self.ms({'op': 'cf-form-attribute-set', 'formXml': form, 'name': 'Флаг', 'payloadJson': payload({'title': 'Флажок'})})
        self.ms({'op': 'cf-form-attribute-delete', 'formXml': form, 'name': 'ТоварыФормы.Количество'})
        table = answer(self.ms({'op': 'cf-form-item-add', 'formXml': form, 'payloadJson': payload({
            'table': 'ТаблицаТоваров', 'dataPath': 'ТоварыФормы',
            'items': [{'input': 'ТаблицаТоваровЦена', 'dataPath': 'ТоварыФормы.Цена'},
                      {'input': 'ТаблицаТоваровПримечание', 'dataPath': 'ТоварыФормы.Примечание'}]})}))
        self.ms({'op': 'cf-form-attribute-rename', 'formXml': form, 'oldName': 'ТоварыФормы', 'newName': 'Товары'})
        self.ms({'op': 'cf-form-command-add', 'formXml': form,
                 'payloadJson': payload({'name': 'Заполнить', 'title': 'Заполнить', 'toolTip': 'Заполнить товары'})})
        self.ms({'op': 'cf-form-command-rename', 'formXml': form, 'oldName': 'Заполнить', 'newName': 'ЗаполнитьТовары'})
        self.ms({'op': 'cf-form-command-set', 'formXml': form, 'name': 'ЗаполнитьТовары',
                 'payloadJson': payload({'action': 'ЗаполнитьТовары'})})
        self.ms({'op': 'cf-form-parameter-add', 'formXml': parameters_form,
                 'payloadJson': payload({'name': 'Ключ', 'type': {'types': ['cfg:CatalogRef.Спр']}, 'key': True})})
        self.ms({'op': 'cf-form-parameter-add', 'formXml': parameters_form,
                 'payloadJson': payload({'name': 'Отбор', 'type': string})})
        self.ms({'op': 'cf-form-parameter-set', 'formXml': parameters_form, 'name': 'Отбор',
                 'payloadJson': payload({'key': False})})
        self.ms({'op': 'cf-form-event-set', 'formXml': form, 'event': 'OnOpen', 'handler': 'ПриОткрытии'})
        self.ms({'op': 'cf-form-event-set', 'formXml': form, 'itemId': table, 'event': 'Selection',
                 'handler': 'ТаблицаТоваровВыбор'})
        content = self.ms_read({'op': 'cf-form-content-get', 'formXml': form})
        price = next(i['id'] for t in content['items'] if t['name'] == 'ТаблицаТоваров'
                     for i in t['items'] if i['name'] == 'ТаблицаТоваровЦена')
        self.ms({'op': 'cf-form-event-set', 'formXml': form, 'itemId': price, 'event': 'OnChange',
                 'handler': 'ТаблицаТоваровЦенаПриИзменении'})
        module = form_dir / 'Ext' / 'Form' / 'Module.bsl'
        module.parent.mkdir(parents=True, exist_ok=True)
        # переводы строк модуля - как у выгрузки платформы: CRLF
        with open(module, 'a', encoding='utf-8-sig', newline='\r\n') as out:
            out.write('\n'.join([
                '&НаКлиенте', 'Процедура ПриОткрытии(Отказ)', 'КонецПроцедуры', '',
                '&НаКлиенте', 'Процедура ТаблицаТоваровВыбор(Элемент, ВыбраннаяСтрока, Поле, СтандартнаяОбработка)',
                'КонецПроцедуры', '',
                '&НаКлиенте', 'Процедура ТаблицаТоваровЦенаПриИзменении(Элемент)', 'КонецПроцедуры', '',
                '&НаКлиенте', 'Процедура ЗаполнитьТовары(Команда)', 'КонецПроцедуры', '']))

    def load_cf(self, name, cf):
        """Обычный config import с проверкой, config check и apply, config export; None - отвергнуто."""
        self.create(name)
        if not self.ib_ok(name, ['config', 'import'], [str(cf)], 'config import') or not self.check_apply(name):
            return None
        out = self.work / (name + '-out')
        if not self.ib_ok(name, ['config', 'export'], [str(out)], 'config export'):
            raise Failure('выгрузка конфигурации ' + name)
        return out

    def external(self):
        log('--- external: внешние отчёт и обработка с формами')
        if tuple(int(x) for x in self.platform.split('.')[:3]) < (8, 3, 23):
            log('  пропущено: ibcmd до 8.3.23 внешние объекты не собирает')
            self.record('external', 'skipped', note='ibcmd до 8.3.23')
            return
        root = self.work / 'ext'
        root.mkdir()
        total_same, total_diffs, total_unstable, rejected = 0, [], [], False
        for name, kind, main in (('ВнешнийОтчет', 'REPORT', 'ExternalReportObject'),
                                 ('ВнешняяОбработка', 'DATA_PROCESSOR', 'ExternalDataProcessorObject')):
            self.ms({'op': 'external-artifact-add', 'artifactsRoot': str(root), 'name': name, 'kind': kind})
            obj = str(root / name / (name + '.xml'))
            self.ms({'op': 'cf-form-add', 'objectXml': obj, 'name': 'Форма'})
            payload = {'mainAttribute': {'name': 'Объект', 'type': 'cfg:%s.%s' % (main, name)}}
            self.ms({'op': 'cf-form-compile', 'objectXml': obj, 'name': 'ФормаОбъекта',
                     'payloadJson': json.dumps(payload, ensure_ascii=False)})
            out = self.build_external(name, kind, Path(obj), 'ext-out')
            if out is None:
                total_diffs.append(name)
                rejected = True
                continue
            same, diffs = self.compare(name, root / name, out)
            total_same += same
            total_diffs += [name + '/' + d for d in diffs]
            log('  второй цикл %s: сборка из выгрузки платформы' % name)
            again = self.build_external(name, kind, out / (name + '.xml'), 'ext-out-2')
            if again is None:
                total_unstable.append(name)
                continue
            _, unstable = self.compare(name + ' (второй цикл)', out, again, ('выгрузка 1', 'выгрузка 2'))
            total_unstable += [name + '/' + d for d in unstable]
        if rejected:
            status = 'rejected'
        elif total_unstable:
            status = 'unstable-roundtrip'
        else:
            status = 'accepted-normalized' if total_diffs else 'pass'
        self.record('external', status, total_same, total_diffs, unstable=total_unstable)

    def build_external(self, name, kind, obj, folder):
        """config import --out и config export --file: каталог выгрузки или None, если платформа отвергла."""
        binary = self.work / folder / (name + ('.erf' if kind == 'REPORT' else '.epf'))
        out = self.work / folder / name
        out.mkdir(parents=True)
        if not self.ib_ok('probe', ['config', 'import'], ['--out=%s' % binary, str(obj)], 'import --out ' + name):
            return None
        if not self.ib_ok('probe', ['config', 'export'], ['--file=%s' % binary, str(out)], 'export --file ' + name):
            return None
        return out

    def all_kinds(self):
        log('--- all-kinds: объекты всех видов формата с дочерними узлами')
        if not self.has_no_check:
            log('  пропущено: в ibcmd нет import files --no-check, а голые регистры и планы проверку не проходят')
            self.record('all-kinds', 'skipped', note='нет import files --no-check')
            return
        cf = self.work / 'all'
        self.ms({'op': 'init-empty-cf', 'targetCfRoot': str(cf)})
        lang = self.work / 'all-lang'
        shutil.copytree(cf, lang)
        conf = str(cf / 'Configuration.xml')
        absent = []
        for kind in ALL_KINDS:
            params = {'op': 'add-md-object', 'configurationXml': conf, 'type': kind}
            owner = next((n for k, _, n, _ in NODES if k == kind), None)
            if owner:
                params['name'] = owner
            else:
                params['autoName'] = True
            try:
                self.ms(params)
            except Failure as e:
                if 'появился в формате' in str(e):
                    absent.append(kind)
                    continue
                raise
        log('  видов нет в формате:', ', '.join(absent) or 'нет')
        for kind, folder, owner, ops in NODES:
            obj = str(cf / folder / (owner + '.xml'))
            for op, extra in ops:
                self.ms({'op': op, 'objectXml': obj, **extra})
        out = self.load_files('all', lang, cf)
        if out is None:
            self.record('all-kinds', 'rejected')
            return
        self.verdict('all-kinds', cf, out, lambda first: self.load_files('all-2', lang, first))

    def load_files(self, name, lang, cf):
        """Язык обычным config import, остальное import files --no-check, затем export.

        Голые регистры и планы проверку не проходят, поэтому config check и apply здесь нет.
        """
        self.create(name)
        if not self.ib_ok(name, ['config', 'import'], [str(lang)], 'config import языка'):
            raise Failure('конфигурация с одним языком не загрузилась')
        files = [str(p) for p in cf.rglob('*') if p.is_file() and p.name != DUMP_INFO]
        if not self.ib_ok(name, ['config', 'import', 'files'], ['--base-dir=%s' % cf, '--no-check', *files],
                          'import files --no-check'):
            return None
        out = self.work / (name + '-out')
        if not self.ib_ok(name, ['config', 'export'], [str(out)], 'config export'):
            raise Failure('выгрузка конфигурации ' + name)
        return out

    def extension(self):
        log('--- extension: расширение со своим справочником и заимствованными объектами всех видов своей конфигурации')
        main = self.work / 'cfe-main'
        self.ms({'op': 'init-empty-cf', 'targetCfRoot': str(main)})
        conf = str(main / 'Configuration.xml')
        absent = []
        for kind in BORROW_KINDS:
            try:
                self.ms({'op': 'add-md-object', 'configurationXml': conf, 'type': kind, 'autoName': True})
            except Failure as e:
                if 'появился в формате' in str(e):
                    absent.append(kind)
                    continue
                raise
        log('  видов нет в формате:', ', '.join(absent) or 'нет')
        for folder, default, changed in ORIGINAL_VALUES:
            for obj in (main / folder).glob('*.xml'):
                obj.write_bytes(obj.read_bytes().replace(default, changed))
        self.create('cfe-main')
        if not self.ib_ok('cfe-main', ['config', 'import'], [str(main)], 'config import основной конфигурации'):
            self.record('extension', 'rejected', note='основная конфигурация')
            return
        cfe = self.work / 'cfe'
        self.ms({'op': 'init-empty-cfe', 'targetCfeRoot': str(cfe), 'name': 'Расширение', 'namePrefix': 'расш_',
                 'mainConfigurationXml': conf})
        cfe_conf = str(cfe / 'Configuration.xml')
        self.ms({'op': 'add-md-object', 'configurationXml': cfe_conf, 'type': 'CATALOG', 'name': 'расш_Справочник'})
        for obj in sorted(p for p in main.glob('*/*.xml')):
            self.ms({'op': 'cfe-borrow-object', 'objectXml': str(obj), 'configurationXml': cfe_conf})
        # Изменённое свойство заимствованного объекта платформа отмечает в InternalInfo: какое
        # состояние она сохраняет у флагов общего модуля, видно по выгрузке
        for module in sorted((cfe / 'CommonModules').glob('*.xml')):
            def flags(dto):
                for flag in ('server', 'serverCall', 'externalConnection'):
                    dto['commonModule'][flag] = not dto['commonModule'][flag]
            self.edit_object(str(module), flags)
        rc, out = self.ib('cfe-main', ['config', 'import'], ['--extension=Расширение', str(cfe)])
        if rc != 0 and self.has_extension_create:
            # ibcmd 8.3.20 не загружает этим путём даже выгрузку расширения самой платформы
            # («Роль.ОсновнаяРоль - Дублирование имени»). Ограничением версии отказ считается, только если
            # все его ошибки - те же, что у контроля: любая другая ошибка - дефект файлов md-sparrow
            accepted, control_errors = self.control_extension_import()
            ours = ibcmd_errors(out)
            if accepted is False and ours and ours <= control_errors:
                log('  ограничение ibcmd: те же ошибки, что при загрузке выгрузки самой платформы:')
                for line in sorted(ours):
                    log('    ' + line[:300])
                self.record('extension', 'platform', note='ibcmd не загружает расширение с ролью (контроль)')
                return
        if rc != 0:
            log('  платформа отвергла (import --extension, код %s):' % rc)
            for line in out.splitlines()[-25:]:
                log('    ' + line[:300])
            self.record('extension', 'rejected')
            return
        if not self.check_apply('cfe-main') or not self.check_apply('cfe-main', 'Расширение'):
            self.record('extension', 'rejected', note='config check или apply')
            return
        exported = self.export_extension('cfe-main', 'cfe-out')
        self.verdict('extension', cfe, exported, lambda first: self.load_extension_again(main, first))

    def export_extension(self, name, folder):
        exported = self.work / folder
        if not self.ib_ok(name, ['config', 'export'], ['--extension=Расширение', str(exported)],
                          'export --extension'):
            raise Failure('выгрузка расширения')
        return exported

    def load_extension_again(self, main, first):
        """Второй цикл расширения: новая база с той же основной конфигурацией и выгрузкой расширения."""
        self.create('cfe-main-2')
        if not self.ib_ok('cfe-main-2', ['config', 'import'], [str(main)], 'config import основной конфигурации'):
            raise Failure('основная конфигурация сценария extension не загрузилась повторно')
        if not self.ib_ok('cfe-main-2', ['config', 'import'], ['--extension=Расширение', str(first)],
                          'import --extension выгрузки'):
            return None
        if not self.check_apply('cfe-main-2') or not self.check_apply('cfe-main-2', 'Расширение'):
            return None
        return self.export_extension('cfe-main-2', 'cfe-out-2')

    def control_extension_import(self):
        """Принимает ли ibcmd тем же путём выгрузку расширения, которое создала сама платформа.

        :return: (True - приняла, False - отвергла, None - контроль не выполнен; строки ошибок отказа)
        """
        self.create('control')
        # 8.3.20 создаёт расширение, но завершается ненулевым кодом: смотрим на выгрузку
        self.ib('control', ['config', 'extension', 'create'],
                ['--name=Контроль', '--name-prefix=к_', '--purpose=add-on'])
        exported = self.work / 'control-out'
        rc, _ = self.ib('control', ['config', 'export'], ['--extension=Контроль', str(exported)])
        if rc != 0:
            log('  контроль не выполнен: платформа не выгрузила своё расширение')
            return None, set()
        self.create('control-import')
        rc, out = self.ib('control-import', ['config', 'import'], ['--extension=Контроль', str(exported)])
        if rc == 0:
            log('  контроль: собственную выгрузку расширения платформа этим путём приняла')
            return True, set()
        log('  контроль: собственную выгрузку расширения платформа этим путём тоже отвергла:')
        for line in sorted(ibcmd_errors(out)):
            log('    ' + line[:300])
        return False, ibcmd_errors(out)

    # ---------- сценарий dcs ----------
    def dcs_env(self, cf):
        """Окружение схем: пустая конфигурация и объекты, на которые ссылаются схемы (пишет только md-sparrow)."""
        self.ms({'op': 'init-empty-cf', 'targetCfRoot': str(cf)})
        conf = str(cf / 'Configuration.xml')
        for kind, folder, name, ops in DCS_ENV:
            self.ms({'op': 'add-md-object', 'configurationXml': conf, 'type': kind, 'name': name})
            obj = str(cf / folder / (name + '.xml'))
            for op, extra in ops:
                self.ms({'op': op, 'objectXml': obj, **extra})
        for folder, name, types in DCS_ENV_TYPES:
            def set_types(dto, types=types):
                for attribute in dto['attributes']:
                    if attribute['name'] in types:
                        attribute['type'] = types[attribute['name']]
            self.edit_object(str(cf / folder / (name + '.xml')), set_types)

    def dcs_params(self, cf, step):
        """Параметры шага схемы: @ключ - путь внутри cf (DCS_PATHS), payload - payloadJson, шаги пакета - так же."""
        params = {}
        for key, value in step.items():
            if key == 'payload':
                params['payloadJson'] = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False)
            elif key == 'operations':
                params[key] = [dict(self.dcs_params(cf, op), schemaVersion=self.version) for op in value]
            elif isinstance(value, str) and value.startswith('@'):
                params[key] = str(cf / DCS_PATHS[value[1:]])
            else:
                params[key] = value
        return params

    def dcs_main(self, cf):
        """Основная конфигурация: элементы сетки (DCS_GRID и входы редактора) -> (записи «ожидаемого», входы).

        После каждого элемента запоминаются строки файлов СКД, которые он записал; в итог идут только строки, дожившие
        до конца подготовки (следующие элементы могли их переписать). По этим строкам сверка после выгрузки называет
        элементы, которые изменила платформа, а вердикт сценария даёт побайтная сверка файлов.
        """
        inputs = load_dcs_inputs(self.dcs_inputs)
        entries = dcs_grid(inputs)
        log('  сетка: элементов %d (запросов конструктора %d, пакетов мастера %d)' % (
            len(entries), len(inputs['queries']), len(inputs['wizard'])))
        before = grid_snapshot(cf)
        records = []
        for entry in entries:
            ops = entry['steps']
            calls = [{'op': 'batch', 'operations': ops}] if entry['batch'] and len(ops) > 1 else ops
            for call in calls:
                try:
                    out = self.ms(self.dcs_params(cf, call))
                except Failure as e:
                    raise Failure('элемент сетки %s: %s' % (entry['id'], e))
                # ответ автозаполнения - какие поля дописаны: по нему видно, что текст разобран
                added = re.findall(r'\{\\?"added\\?":\[[^\]]*\]', out)
                if added:
                    log('  %s: %s' % (entry['id'], '; '.join(a.replace('\\"', '"') for a in added)[:300]))
            after = grid_snapshot(cf)
            records.append({'id': entry['id'], 'group': entry['group'], 'what': entry['what'],
                            'ops': [o['op'] for o in ops], 'expected': grid_added(before, after)})
            before = after
        for record in records:
            record['expected'] = grid_surviving(record['expected'], before)
        return records, inputs

    def dcs_self_check(self, cf):
        """Самопроверка подготовленного: строгая cf-dcs-validate каждой схемы, ссылки схем на окружение и пустая
        схема против эталона платформы (если submodule эталонов рядом)."""
        contents, _, _ = dcs_layout([cf])
        strict = []
        for rel in sorted(contents):
            answer = self.ms_read({'op': 'cf-dcs-validate', 'objectXml': str(cf / rel), 'strict': True})
            ok = answer.get('ok') is True and not answer.get('errors') and not answer.get('warnings')
            strict.append({'file': rel, 'ok': ok, 'errors': answer.get('errors') or [],
                           'warnings': answer.get('warnings') or []})
            if not ok:
                log('  строгая проверка не прошла:', rel, json.dumps(answer, ensure_ascii=False)[:400])
        refs = dcs_env_refs(cf)
        for item in refs['missing']:
            log('  ссылка схемы не найдена в окружении:', item['file'], item['ref'])
        empty = empty_schema_check(cf)
        if empty['ok'] is False:
            log('  пустая схема не совпала с эталоном платформы:', empty['file'], empty['etalon'])
        # emptySchemaOk: None - эталона рядом нет (не ошибка), False - md-sparrow пишет пустую схему не байтами платформы
        return {'strictOk': bool(strict) and all(s['ok'] for s in strict), 'envRefsOk': refs['ok'],
                'emptySchemaOk': empty['ok'], 'strict': strict, 'envRefs': refs, 'emptySchema': empty}

    def dcs_prepare(self, root):
        """Подготовка сценария dcs без платформы: окружение, схемы, самопроверка -> раздел манифеста."""
        cf = root / 'dcs' / 'cf'
        log('--- dcs (подготовка): окружение и схемы, формат %s' % self.format)
        self.dcs_env(cf)
        records, inputs = self.dcs_main(cf)
        check = self.dcs_self_check(cf)
        contents, descriptions, owners = dcs_layout([cf])
        files = sorted(contents | descriptions | set(owners))
        # grid - «ожидаемое»: что записал каждый элемент сетки; inputs - откуда тексты конструктора и пакеты мастера
        return {'main': 'dcs/cf', 'dcsFiles': ['dcs/cf/' + rel for rel in files], 'selfCheck': check,
                'grid': records, 'gridOps': sorted({op for r in records for op in r['ops']}),
                'inputs': {'file': Path(self.dcs_inputs).name, 'sha256': sha256(self.dcs_inputs),
                           'queries': len(inputs['queries']), 'wizard': len(inputs['wizard']),
                           'generator': inputs.get('generator')}}

    def prepare(self, root, scenarios):
        """Каталог подготовки: дерево md-sparrow и manifest.json; -> манифест."""
        # счётчик вызовов - этой подготовки: в манифесте он должен повторяться от запуска к запуску
        self.calls = 0
        root.mkdir(parents=True, exist_ok=True)
        sections = {}
        for name in scenarios:
            if name == 'dcs':
                sections[name] = self.dcs_prepare(root)
        manifest = {
            'tool': 'roundtrip.py', 'manifestVersion': MANIFEST_VERSION, 'format': self.format,
            'jar': {'name': self.jar.name, 'sha256': sha256(self.jar)},
            'created': datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat(),
            'calls': self.calls, 'scenarios': sections, 'files': tree_hashes(root)}
        (root / MANIFEST).write_text(json.dumps(manifest, ensure_ascii=False, indent=1) + '\n', encoding='utf-8')
        return manifest

    def dcs(self):
        """Полный режим: подготовка в рабочем каталоге и сразу загрузка."""
        if not dcs_format_ok(self.format):
            log('--- dcs: пропущено, формат %s вне 2.12-2.20' % self.format)
            self.record('dcs', 'skipped', note='формат %s вне 2.12-2.20' % self.format, envDiffs=[], typeOrder=[])
            return
        root = self.work / 'dcs-prepared'
        manifest = self.prepare(root, ['dcs'])
        check = manifest['scenarios']['dcs']['selfCheck']
        if not self_check_ok(check):
            raise Failure('подготовка dcs не прошла самопроверку (строгая проверка, ссылки на окружение или эталон '
                          'пустой схемы)')
        self.dcs_verify(root, manifest)

    def dcs_verify(self, root, manifest):
        """Загрузка подготовленного сценария dcs, сверка по группам файлов и второй цикл."""
        say = self.say
        say('--- dcs: схемы компоновки данных, config import с проверкой')
        if not dcs_format_ok(self.format):
            say('  пропущено: формат %s вне 2.12-2.20' % self.format)
            self.record('dcs', 'skipped', note='формат %s вне 2.12-2.20' % self.format, envDiffs=[], typeOrder=[])
            return
        cf = root / manifest['scenarios']['dcs']['main']
        first = self.load_cf('dcs', cf)
        if first is None:
            self.record('dcs', 'rejected', envDiffs=[], typeOrder=[])
            return
        if self.save_exports:
            target = self.save_exports / 'dcs'
            shutil.rmtree(target, ignore_errors=True)
            shutil.copytree(first, target)
        same, dcs_diffs, env_diffs, type_order = compare_dcs('dcs', cf, first, say=say)
        # «ожидаемое» манифеста: какие элементы сетки платформа изменила и совпала ли пустая схема с эталоном
        extra = dcs_expected_report(manifest['scenarios']['dcs'], first, say)
        say('  второй цикл dcs: загрузка первой выгрузки платформы')
        second = self.load_cf('dcs-2', first)
        if second is None:
            self.record('dcs', 'unstable-roundtrip', same, dcs_diffs, note='платформа отвергла свою выгрузку',
                        envDiffs=env_diffs, typeOrder=type_order, **extra)
            return
        _, unstable, env_unstable, _ = compare_dcs('dcs (второй цикл)', first, second, ('выгрузка 1', 'выгрузка 2'),
                                                   say=say)
        self.record('dcs', dcs_status(dcs_diffs, unstable), same, dcs_diffs, unstable=unstable,
                    envDiffs=env_diffs + ['второй цикл: ' + d for d in env_unstable], typeOrder=type_order, **extra)


TYPE_LINE = re.compile(rb'^\s*<v8:Type(?:\s[^>]*)?>[^<]*</v8:Type>\r?$')
TYPESET_LINE = re.compile(rb'^\s*<v8:TypeSet(?:\s[^>]*)?>[^<]*</v8:TypeSet>\r?$')


def type_series(data, typesets=False):
    """Строки файла, где каждая серия <v8:Type> подряд заменена отсортированной: порядок типов не важен.

    С typesets серии <v8:TypeSet> сортируются так же, но отдельно: тип и набор типов не перемешиваются.
    """
    lines, out, run_, kind = data.split(b'\n'), [], [], None
    for line in lines + [b'']:
        current = 'type' if TYPE_LINE.match(line) else 'set' if typesets and TYPESET_LINE.match(line) else None
        if current and current == kind:
            run_.append(line)
            continue
        out += sorted(run_)
        run_, kind = ([line], current) if current else ([], None)
        if not current:
            out.append(line)
    return out


def log_difference(da, db, what, limit=60, say=log):
    """Различие двух версий файла в журнал: переводы строк или первые строки unified diff."""
    if da.replace(b'\r\n', b'\n') == db.replace(b'\r\n', b'\n'):
        say('    отличаются только переводы строк')
        return
    la = da.decode('utf-8', 'replace').splitlines()
    lb = db.decode('utf-8', 'replace').splitlines()
    for line in list(difflib.unified_diff(la, lb, what[0], what[1], lineterm='', n=1))[:limit]:
        say('    ' + line[:240])


def dcs_format_ok(fmt):
    try:
        version = tuple(int(x) for x in fmt.split('.'))
    except (AttributeError, ValueError):
        return False
    return DCS_FORMATS[0] <= version <= DCS_FORMATS[1]


def dcs_status(dcs_diffs, unstable):
    """Вердикт сценария dcs по файлам СКД: окружение на него не влияет."""
    if unstable:
        return 'unstable-roundtrip'
    return 'accepted-normalized' if dcs_diffs else 'pass'


def files_of(root):
    return {p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_file() and p.name != DUMP_INFO}


def dcs_layout(trees):
    """Файлы СКД по деревьям: содержимое макетов, описания макетов и владельцы {файл: {(тег, имя макета)}}.

    Макет СКД узнаётся по корню содержимого (DataCompositionSchema) с любой стороны сверки: если одна сторона
    файл потеряла, он всё равно относится к СКД.
    """
    contents, descriptions, owners = set(), set(), {}
    for root in trees:
        for path in root.rglob('Template.xml'):
            rel = path.relative_to(root).as_posix()
            m = TEMPLATE_CONTENT.match(rel)
            if not m or not path.is_file():
                continue
            with open(path, 'rb') as f:
                head = f.read(4096)
            if not DCS_ROOT.search(head):
                continue
            contents.add(rel)
            if m.group(3):
                descriptions.add('CommonTemplates/%s.xml' % m.group(3))
                owners.setdefault('Configuration.xml', set()).add(('CommonTemplate', m.group(3)))
            else:
                descriptions.add('%s/Templates/%s.xml' % (m.group(1), m.group(2)))
                owners.setdefault(m.group(1) + '.xml', set()).add(('Template', m.group(2)))
    return contents, descriptions, owners


def owner_split(data, names):
    """Владелец макетов: (части, которые пишет редактор СКД, остаток файла).

    Части - свойство MainDataCompositionSchema и строки состава с именами макетов СКД в порядке файла (порядок
    макетов в составе - тоже дело редактора); остаток - окружение.
    """
    text = data.decode('utf-8', 'replace')
    patterns = [MAIN_DCS] + [re.compile(r'[ \t]*<%s>%s</%s>[ \t]*\r?\n?' % (tag, re.escape(name), tag))
                             for tag, name in sorted(names)]
    parts, rest, pos = [], [], 0
    for start, end in sorted({m.span() for p in patterns for m in p.finditer(text)}):
        if start < pos:
            continue
        rest.append(text[pos:start])
        parts.append(text[start:end].strip())
        pos = end
    rest.append(text[pos:])
    return parts, ''.join(rest).encode('utf-8')


def compare_dcs(title, ours, theirs, what=('md-sparrow', 'платформа'), say=log):
    """Сверка сценария dcs по двум группам: файлы СКД и окружение.

    Серии <v8:Type> и, отдельно, <v8:TypeSet> сравниваются как мультимножества; файлы, совпавшие только так,
    перечисляются в type_order - так виден порядок платформы.
    :return: (совпало файлов, различия СКД, различия окружения, совпавшие с точностью до порядка типов)
    """
    ours_files, theirs_files = files_of(ours), files_of(theirs)
    contents, descriptions, owners = dcs_layout([ours, theirs])
    same, dcs_diffs, env_diffs, type_order = 0, [], [], []
    for rel in sorted(ours_files | theirs_files):
        group = 'dcs' if rel in contents or rel in descriptions else 'owner' if rel in owners else 'env'
        diffs = env_diffs if group == 'env' else dcs_diffs
        mark = '' if group == 'env' else ' [СКД]'
        if rel not in theirs_files:
            diffs.append(rel)
            say('  НЕТ В ВЫГРУЗКЕ%s %s' % (mark, rel))
            continue
        if rel not in ours_files:
            diffs.append(rel)
            say('  ТОЛЬКО В ВЫГРУЗКЕ%s %s' % (mark, rel))
            continue
        da, db = (ours / rel).read_bytes(), (theirs / rel).read_bytes()
        if da == db:
            same += 1
            continue
        if type_series(da, True) == type_series(db, True):
            same += 1
            type_order.append(rel)
            say('  совпало с точностью до порядка типов ' + rel)
            continue
        if group == 'owner':
            parts_a, rest_a = owner_split(da, owners[rel])
            parts_b, rest_b = owner_split(db, owners[rel])
            dcs_part = parts_a != parts_b
            env_part = type_series(rest_a, True) != type_series(rest_b, True)
            if dcs_part:
                dcs_diffs.append(rel)
                say('  РАЗЛИЧИЕ [СКД: свойство схемы или состав макетов] ' + rel)
                for line in difflib.unified_diff(parts_a, parts_b, what[0], what[1], lineterm='', n=0):
                    say('    ' + line[:240])
            if env_part or not dcs_part:
                env_diffs.append(rel)
                say('  РАЗЛИЧИЕ [окружение] ' + rel)
                log_difference(rest_a, rest_b, what, 30, say)
            continue
        diffs.append(rel)
        say('  РАЗЛИЧИЕ%s %s' % (mark, rel))
        log_difference(da, db, what, 60 if group != 'env' else 30, say)
    say('  итог %s: совпало %d, различий СКД %d, окружения %d, только порядок типов %d' % (
        title, same, len(dcs_diffs), len(env_diffs), len(type_order)))
    return same, dcs_diffs, env_diffs, type_order


def dcs_env_refs(cf):
    """Ссылки схем на окружение: каждый cfg:<Вид>Ref.<Имя>, значение конфигуратора и TypeId есть в конфигурации.

    :return: {'ok', 'refs', 'values', 'typeIds', 'missing': [{'file', 'ref'}]}
    """
    conf = (cf / 'Configuration.xml').read_text(encoding='utf-8-sig')
    child = conf[conf.find('<ChildObjects'):]
    contained = {(m.group(1), m.group(2)) for m in CHILD_OBJECT.finditer(child)}
    contents, _, _ = dcs_layout([cf])
    # UUID сгенерированных типов окружения: определяемый тип схема пишет одним TypeId
    known_ids = set()
    for path in cf.rglob('*.xml'):
        if path.relative_to(cf).as_posix() not in contents:
            known_ids.update(u.lower() for u in re.findall(r'[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-'
                                                            r'[0-9a-fA-F]{4}-[0-9a-fA-F]{12}',
                                                            path.read_text(encoding='utf-8-sig', errors='replace')))
    missing, refs, values, ids = [], 0, 0, 0
    for rel in sorted(contents):
        text = (cf / rel).read_text(encoding='utf-8-sig', errors='replace')
        for m in REF_TYPE.finditer(text):
            kind = REF_KINDS.get(m.group(2))
            refs += 1
            if kind is None or (kind, m.group(3)) not in contained:
                missing.append({'file': rel, 'ref': 'cfg:%s.%s' % (m.group(2), m.group(3))})
        for m in DESIGN_TIME.finditer(text):
            values += 1
            parts = m.group(1).strip().split('.')
            kind = RU_KINDS.get(parts[0])
            if kind is None or len(parts) < 2 or (kind[0], parts[1]) not in contained:
                missing.append({'file': rel, 'ref': m.group(1).strip()})
                continue
            # значение перечисления проверяется по составу перечисления; ПустаяСсылка есть у любого объекта
            if kind[0] == 'Enum' and len(parts) > 2 and parts[2] != 'ПустаяСсылка':
                enum = cf / kind[1] / (parts[1] + '.xml')
                if not enum.is_file() or ('<Name>%s</Name>' % parts[2]) not in enum.read_text(encoding='utf-8-sig'):
                    missing.append({'file': rel, 'ref': m.group(1).strip()})
        for m in TYPE_ID.finditer(text):
            ids += 1
            if m.group(1).lower() not in known_ids:
                missing.append({'file': rel, 'ref': 'TypeId ' + m.group(1)})
    return {'ok': not missing, 'refs': refs, 'values': values, 'typeIds': ids, 'missing': missing}


# ---------- основная сетка: «ожидаемое» и сверка по элементам ----------
# Строка с содержимым: текст между тегами, атрибут или продолжение многострочного текста. Голые открывающие и
# закрывающие теги есть у любого элемента и ничего не говорят о том, чей он
GRID_TEXT = re.compile(r'>[^<]*[^<\s][^<]*<')


def grid_lines(data):
    """Строки файла, по которым узнаётся записанное: без отступов, только с содержимым (GRID_TEXT, атрибут, текст)."""
    out = []
    for line in data.decode('utf-8-sig', 'replace').splitlines():
        s = line.strip()
        if s and (not s.startswith('<') or '="' in s or GRID_TEXT.search(s)):
            out.append(s)
    return out


def grid_snapshot(cf):
    """Строки файлов СКД дерева {файл: [строки]}: содержимое и описания макетов целиком, у владельцев - только части
    редактора (свойство основной схемы и строки состава макетов СКД), остальное у владельца - окружение."""
    contents, descriptions, owners = dcs_layout([cf])
    out = {rel: grid_lines((cf / rel).read_bytes()) for rel in contents | descriptions}
    for rel, names in owners.items():
        parts, _ = owner_split((cf / rel).read_bytes(), names)
        out[rel] = grid_lines('\n'.join(parts).encode('utf-8'))
    return out


def grid_added(before, after):
    """Строки, которые появились между снимками (вставлены или заменены): {файл: [строки]}."""
    out = {}
    for rel, lines in sorted(after.items()):
        matcher = difflib.SequenceMatcher(None, before.get(rel, []), lines, autojunk=False)
        added = [line for tag, _i1, _i2, j1, j2 in matcher.get_opcodes() if tag in ('insert', 'replace')
                 for line in lines[j1:j2]]
        if added:
            out[rel] = added
    return out


def grid_surviving(expected, final):
    """Из строк элемента - те, что есть в итоговом снимке (с учётом числа повторов)."""
    out = {}
    for rel, lines in expected.items():
        have = Counter(final.get(rel, []))
        kept = []
        for line in lines:
            if have[line] > 0:
                have[line] -= 1
                kept.append(line)
        if kept:
            out[rel] = kept
    return out


def grid_check(records, root):
    """Элементы сетки, чьих строк нет в дереве root (выгрузке): [{id, file, missing, missingCount}].

    Строки сравниваются мультимножеством по файлу: порядок и отступы - дело побайтной сверки, здесь важно, чьё
    записанное платформа изменила или потеряла."""
    cache, changed = {}, []
    for record in records:
        for rel, lines in sorted(record.get('expected', {}).items()):
            if rel not in cache:
                path = root / rel
                cache[rel] = Counter(grid_lines(path.read_bytes())) if path.is_file() else None
            have = cache[rel]
            if have is None:
                changed.append({'id': record['id'], 'file': rel, 'missing': ['(файла нет)'], 'missingCount': len(lines)})
                continue
            missing = [line for line, n in Counter(lines).items() if have[line] < n]
            if missing:
                changed.append({'id': record['id'], 'file': rel, 'missing': missing[:5], 'missingCount': len(missing)})
    return changed


def empty_schema_check(cf):
    """Пустая схема сетки против эталона платформы: ok - True | False | None (эталона рядом нет)."""
    path = cf / DCS_PATHS['empty']
    digest = sha256(path) if path.is_file() else None
    if not EMPTY_SCHEMA_ETALON.is_file():
        return {'ok': None, 'file': DCS_PATHS['empty'], 'etalon': None, 'sha256': digest}
    etalon = EMPTY_SCHEMA_ETALON.relative_to(REPO_ROOT).as_posix()
    return {'ok': digest is not None and digest == sha256(EMPTY_SCHEMA_ETALON), 'file': DCS_PATHS['empty'],
            'etalon': etalon, 'sha256': digest}


def self_check_ok(check):
    """Самопроверка подготовки прошла: строгая проверка и ссылки - обязательно, эталон пустой схемы - если сверялся."""
    return bool(check.get('strictOk')) and bool(check.get('envRefsOk')) and check.get('emptySchemaOk') is not False


def dcs_expected_report(section, export, say=log):
    """Сверка выгрузки с «ожидаемым» манифеста: изменённые платформой элементы сетки и пустая схема против эталона.

    :return: дополнительные поля results.dcs (grid, emptySchema)
    """
    records = section['grid']
    changed = grid_check(records, export)
    ids = sorted({c['id'] for c in changed}, key=[r['id'] for r in records].index)
    say('  сетка: элементов %d, изменены платформой %d%s' % (len(records), len(ids), (': ' + ', '.join(ids)) if ids else ''))
    for c in changed[:40]:
        say('    %s, %s: нет %d строк, например %s' % (c['id'], c['file'], c['missingCount'], c['missing'][0][:160]))
    extra = {'grid': {'entries': len(records), 'changedIds': ids, 'changed': changed}}
    empty = section['selfCheck']['emptySchema']
    if empty['sha256']:
        path = export / empty['file']
        state = 'missing' if not path.is_file() else 'same' if sha256(path) == empty['sha256'] else 'differs'
        say('  пустая схема в выгрузке: %s' % {'missing': 'нет файла', 'same': 'те же байты, что записаны (эталон)',
                                               'differs': 'другие байты'}[state])
        extra['emptySchema'] = state
    return extra


def copy_tree(src, dst):
    shutil.rmtree(dst, ignore_errors=True)
    shutil.copytree(src, dst)


def tree_hashes(root):
    """sha256 файлов подготовленного дерева (без манифеста, метки и служебного рабочего каталога)."""
    out = {}
    for path in sorted(root.rglob('*')):
        rel = path.relative_to(root).as_posix()
        if path.is_file() and rel not in (MANIFEST, WORK_MARKER) and not rel.startswith('.work/'):
            out[rel] = sha256(path)
    return out


def load_manifest(root):
    path = root / MANIFEST
    if not path.is_file():
        raise Failure('в %s нет %s: это не каталог подготовки' % (root, MANIFEST))
    try:
        return json.loads(path.read_text(encoding='utf-8'))
    except ValueError as e:
        raise Failure('%s не читается: %s' % (path, e))


def manifest_problems(manifest, root):
    """Что не так с манифестом и подготовленным деревом: строение, самопроверка, целость файлов."""
    if not isinstance(manifest, dict):
        return ['манифест - не объект JSON']
    problems = []
    if manifest.get('tool') != 'roundtrip.py':
        problems.append('tool не roundtrip.py')
    if manifest.get('manifestVersion') != MANIFEST_VERSION:
        problems.append('manifestVersion %r, ожидается %d' % (manifest.get('manifestVersion'), MANIFEST_VERSION))
    if not re.match(r'^\d+\.\d+$', str(manifest.get('format', ''))):
        problems.append('format %r не вида 2.20' % manifest.get('format'))
    jar = manifest.get('jar') if isinstance(manifest.get('jar'), dict) else {}
    if not jar.get('name') or not re.match(r'^[0-9a-f]{64}$', str(jar.get('sha256', ''))):
        problems.append('jar без имени или sha256')
    scenarios = manifest.get('scenarios')
    if not isinstance(scenarios, dict) or not scenarios:
        problems.append('нет подготовленных сценариев')
        scenarios = {}
    for name, section in scenarios.items():
        if name not in PREPARABLE:
            problems.append('сценарий %s не готовится заранее' % name)
            continue
        main = section.get('main') if isinstance(section, dict) else None
        if not main or not (root / main / 'Configuration.xml').is_file():
            problems.append('%s: нет конфигурации %s' % (name, main))
        check = section.get('selfCheck') if isinstance(section, dict) else None
        if not isinstance(check, dict) or check.get('strictOk') is not True or check.get('envRefsOk') is not True \
                or check.get('emptySchemaOk') is False:
            problems.append('%s: самопроверка подготовки не прошла' % name)
        for rel in section.get('dcsFiles') or []:
            if not (root / rel).is_file():
                problems.append('%s: нет файла СКД %s' % (name, rel))
        records = section.get('grid') if isinstance(section, dict) else None
        if not isinstance(records, list) or not all(
                isinstance(r, dict) and r.get('id') and isinstance(r.get('expected'), dict) for r in records):
            problems.append('%s: сетка (grid) - не список элементов с id и expected' % name)
    files = manifest.get('files')
    if not isinstance(files, dict) or not files:
        problems.append('нет списка файлов')
        files = {}
    actual = tree_hashes(root)
    for rel, digest in sorted(files.items()):
        if rel not in actual:
            problems.append('нет файла %s' % rel)
        elif actual[rel] != digest:
            problems.append('файл изменён после подготовки: %s' % rel)
    for rel in sorted(set(actual) - set(files)):
        problems.append('файл не из подготовки: %s' % rel)
    return problems


def ibcmd_errors(output):
    """Строки ошибок ibcmd: по ним отказ файлов md-sparrow сверяется с отказом контроля."""
    return {line.strip() for line in output.splitlines() if '[ERROR]' in line}


def find_ibcmd(bin_dir):
    names = ('ibcmd.exe', 'ibcmd') if os.name == 'nt' else ('ibcmd',)
    if bin_dir:
        for n in names:
            if (Path(bin_dir) / n).is_file():
                return str(Path(bin_dir) / n)
        return None
    found = sorted(p for root in ('/opt/1cv8', '/opt/1C') for p in Path(root).rglob('ibcmd') if p.is_file()) \
        if os.name != 'nt' else []
    return str(found[-1]) if found else None


# ---------- самотест (без платформы) ----------
class SelfTest:
    """Счётчик проверок самотеста: каждая - условие с именем."""

    def __init__(self):
        self.passed, self.failed = 0, []

    def check(self, name, condition, detail=''):
        if condition:
            self.passed += 1
            log('  ok   ' + name)
        else:
            self.failed.append(name)
            log('  СБОЙ ' + name + (': ' + str(detail)[:400] if detail else ''))


# Синтетическое дерево «записанного» для проверки правил сверки: отчёт с основной схемой, общий макет СКД,
# макет-не-СКД и справочник окружения. Текст наш, это не выгрузка платформы
SYNTHETIC_SCHEMA = '\r\n'.join([
    '\ufeff<?xml version="1.0" encoding="UTF-8"?>',
    '<DataCompositionSchema xmlns="http://v8.1c.ru/8.1/data-composition-system/schema" '
    'xmlns:v8="http://v8.1c.ru/8.1/data/core" xmlns:xs="http://www.w3.org/2001/XMLSchema">',
    '\t<parameter>',
    '\t\t<name>П</name>',
    '\t\t<valueType>',
    '\t\t\t<v8:Type>xs:string</v8:Type>',
    '\t\t\t<v8:Type xmlns:d4p1="%s">d4p1:CatalogRef.Валюты</v8:Type>' % CURRENT_CONFIG,
    '\t\t\t<v8:Type>xs:decimal</v8:Type>',
    '\t\t\t<v8:TypeSet xmlns:d4p1="%s">d4p1:CatalogRef</v8:TypeSet>' % CURRENT_CONFIG,
    '\t\t\t<v8:TypeSet xmlns:d4p1="%s">d4p1:EnumRef</v8:TypeSet>' % CURRENT_CONFIG,
    '\t\t</valueType>',
    '\t\t<value xsi:type="dcscor:DesignTimeValue">Перечисление.Статусы.Новый</value>',
    '\t</parameter>',
    '</DataCompositionSchema>'])
SYNTHETIC_TREE = {
    'Configuration.xml': '\r\n'.join([
        '<MetaDataObject version="2.20">', '\t<Configuration>', '\t\t<ChildObjects>',
        '\t\t\t<Language>Русский</Language>', '\t\t\t<Catalog>Валюты</Catalog>', '\t\t\t<Enum>Статусы</Enum>',
        '\t\t\t<CommonTemplate>ОбщийМакет1</CommonTemplate>', '\t\t\t<Report>Отчет1</Report>',
        '\t\t</ChildObjects>', '\t</Configuration>', '</MetaDataObject>']),
    'Catalogs/Валюты.xml': '<MetaDataObject>\r\n\t<Catalog><Synonym>Валюты</Synonym></Catalog>\r\n</MetaDataObject>',
    'Enums/Статусы.xml': '<MetaDataObject>\r\n\t<Enum><Name>Статусы</Name><Name>Новый</Name></Enum>\r\n</MetaDataObject>',
    'Reports/Отчет1.xml': '\r\n'.join([
        '<MetaDataObject>', '\t<Report>', '\t\t<Properties>', '\t\t\t<Synonym>Отчёт</Synonym>',
        '\t\t\t<MainDataCompositionSchema>Report.Отчет1.Template.Схема</MainDataCompositionSchema>',
        '\t\t</Properties>', '\t\t<ChildObjects>', '\t\t\t<Template>Схема</Template>', '\t\t\t<Template>Печать</Template>',
        '\t\t</ChildObjects>', '\t</Report>', '</MetaDataObject>']),
    'Reports/Отчет1/Templates/Схема.xml': '<MetaDataObject><Template><TemplateType>DataCompositionSchema</TemplateType>'
                                          '</Template></MetaDataObject>',
    'Reports/Отчет1/Templates/Схема/Ext/Template.xml': SYNTHETIC_SCHEMA,
    'Reports/Отчет1/Templates/Печать.xml': '<MetaDataObject><Template><TemplateType>SpreadsheetDocument'
                                           '</TemplateType></Template></MetaDataObject>',
    'Reports/Отчет1/Templates/Печать/Ext/Template.xml': '<document xmlns="http://v8.1c.ru/8.2/data/spreadsheet"/>',
    'CommonTemplates/ОбщийМакет1.xml': '<MetaDataObject><CommonTemplate/></MetaDataObject>',
    'CommonTemplates/ОбщийМакет1/Ext/Template.xml': SYNTHETIC_SCHEMA.replace('<name>П</name>', '<name>О</name>'),
}


def write_tree(root, files):
    shutil.rmtree(root, ignore_errors=True)
    for rel, text in files.items():
        path = root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(text.encode('utf-8') if isinstance(text, str) else text)


def edit_file(root, rel, old, new):
    path = root / rel
    data = path.read_bytes()
    if old.encode('utf-8') not in data:
        raise Failure('самотест: в %s нет «%s»' % (rel, old))
    path.write_bytes(data.replace(old.encode('utf-8'), new.encode('utf-8'), 1))


def swap_lines(root, rel, pattern):
    """Переставить первые две подряд идущие строки файла, подходящие под pattern (порядок типов)."""
    path = root / rel
    lines = path.read_bytes().split(b'\n')
    for i in range(len(lines) - 1):
        if pattern.match(lines[i]) and pattern.match(lines[i + 1]) and lines[i] != lines[i + 1]:
            lines[i], lines[i + 1] = lines[i + 1], lines[i]
            path.write_bytes(b'\n'.join(lines))
            return True
    return False


def self_test_rules(t, tmp):
    """Правила сверки и вспомогательные функции на синтетике: без java и ibcmd."""
    log('--- самотест: правила сверки на синтетике')
    quiet = []
    say = quiet.append
    a = b'\t<v8:Type>xs:string</v8:Type>\n\t<v8:Type>xs:decimal</v8:Type>\n'
    b = b'\t<v8:Type>xs:decimal</v8:Type>\n\t<v8:Type>xs:string</v8:Type>\n'
    t.check('серия <v8:Type> - мультимножество', type_series(a) == type_series(b) and type_series(a, True) == type_series(b, True))
    sa = b'\t<v8:TypeSet>cfg:CatalogRef</v8:TypeSet>\n\t<v8:TypeSet>cfg:EnumRef</v8:TypeSet>\n'
    sb = b'\t<v8:TypeSet>cfg:EnumRef</v8:TypeSet>\n\t<v8:TypeSet>cfg:CatalogRef</v8:TypeSet>\n'
    t.check('серия <v8:TypeSet> - мультимножество в сверке dcs', type_series(sa, True) == type_series(sb, True))
    t.check('остальные сценарии серии <v8:TypeSet> не переставляют', type_series(sa) != type_series(sb))
    mixed_a = b'<v8:Type>xs:string</v8:Type>\n<v8:TypeSet>cfg:CatalogRef</v8:TypeSet>\n'
    mixed_b = b'<v8:TypeSet>cfg:CatalogRef</v8:TypeSet>\n<v8:Type>xs:string</v8:Type>\n'
    t.check('тип и набор типов не перемешиваются', type_series(mixed_a, True) != type_series(mixed_b, True))
    t.check('другой тип - различие', type_series(a, True) != type_series(a.replace(b'decimal', b'boolean'), True))
    t.check('путь WSL -> Windows', win_path('/mnt/c/work/Отчёт') == 'C:\\work\\Отчёт'
            and win_path('/mnt/d') == 'D:\\')
    shallow = {p: repo_root(p) for p in ('/work/roundtrip.py', '/roundtrip.py', '/tmp/rt/golden-snapshots/roundtrip.py')}
    t.check('корень репозитория у неглубокой копии скрипта - корень ФС',
            shallow == {'/work/roundtrip.py': Path('/'), '/roundtrip.py': Path('/'),
                        '/tmp/rt/golden-snapshots/roundtrip.py': Path('/tmp')}, shallow)
    try:
        win_path('/tmp/x')
        t.check('путь вне диска Windows - отказ', False)
    except Failure:
        t.check('путь вне диска Windows - отказ', True)
    t.check('форматы dcs: 2.12-2.20', dcs_format_ok('2.12') and dcs_format_ok('2.20') and not dcs_format_ok('2.21')
            and not dcs_format_ok('2.11') and not dcs_format_ok(None))
    t.check('вердикт dcs', dcs_status([], []) == 'pass' and dcs_status(['x'], []) == 'accepted-normalized'
            and dcs_status([], ['x']) == 'unstable-roundtrip')
    try:
        parse_scenarios('forms,нет')
        t.check('неизвестный сценарий - отказ', False)
    except ValueError:
        t.check('неизвестный сценарий - отказ', parse_scenarios('dcs,forms') == ['forms', 'dcs'])

    ours, theirs = tmp / 'syn-ours', tmp / 'syn-theirs'
    write_tree(ours, SYNTHETIC_TREE)
    contents, descriptions, owners = dcs_layout([ours])
    t.check('группы файлов СКД', contents == {'Reports/Отчет1/Templates/Схема/Ext/Template.xml',
                                             'CommonTemplates/ОбщийМакет1/Ext/Template.xml'}
            and descriptions == {'Reports/Отчет1/Templates/Схема.xml', 'CommonTemplates/ОбщийМакет1.xml'}
            and owners == {'Reports/Отчет1.xml': {('Template', 'Схема')},
                           'Configuration.xml': {('CommonTemplate', 'ОбщийМакет1')}}, (contents, descriptions, owners))

    def case(name, mutate, expect):
        """mutate(theirs) имитирует выгрузку платформы; expect - (различия СКД, окружения, порядок типов)."""
        write_tree(theirs, SYNTHETIC_TREE)
        mutate(theirs)
        result = compare_dcs(name, ours, theirs, say=say)
        got = (sorted(result[1]), sorted(result[2]), sorted(result[3]))
        t.check('сверка: ' + name, got == expect, got)
    schema = 'Reports/Отчет1/Templates/Схема/Ext/Template.xml'
    case('совпадение', lambda d: None, ([], [], []))
    case('ConfigDumpInfo.xml не сравнивается', lambda d: (d / DUMP_INFO).write_text('x', encoding='utf-8'), ([], [], []))
    case('порядок <v8:Type>', lambda d: swap_lines(d, schema, TYPE_LINE), ([], [], [schema]))
    case('порядок <v8:TypeSet>', lambda d: swap_lines(d, schema, TYPESET_LINE), ([], [], [schema]))
    case('окружение не влияет на СКД', lambda d: edit_file(d, 'Catalogs/Валюты.xml', 'Валюты<', 'Валюта<'),
         ([], ['Catalogs/Валюты.xml'], []))
    case('владелец: свойство схемы - СКД', lambda d: edit_file(d, 'Reports/Отчет1.xml', 'Template.Схема<', 'Template.Иная<'),
         (['Reports/Отчет1.xml'], [], []))
    case('владелец: прочие свойства - окружение', lambda d: edit_file(d, 'Reports/Отчет1.xml', 'Отчёт<', 'Отчет<'),
         ([], ['Reports/Отчет1.xml'], []))
    case('владелец: состав макетов СКД - СКД', lambda d: edit_file(d, 'Reports/Отчет1.xml', '\t\t\t<Template>Схема</Template>\r\n', ''),
         (['Reports/Отчет1.xml'], [], []))
    case('владелец: макет не СКД - окружение', lambda d: edit_file(d, 'Reports/Отчет1.xml', '<Template>Печать</Template>', '<Template>Печать2</Template>'),
         ([], ['Reports/Отчет1.xml'], []))
    case('конфигурация: общий макет СКД - СКД', lambda d: edit_file(d, 'Configuration.xml', '\t\t\t<CommonTemplate>ОбщийМакет1</CommonTemplate>\r\n', ''),
         (['Configuration.xml'], [], []))
    case('содержимое схемы - СКД', lambda d: edit_file(d, schema, '<name>П</name>', '<name>Р</name>'), ([schema], [], []))
    case('переводы строк схемы - различие', lambda d: (d / schema).write_bytes((d / schema).read_bytes().replace(b'\r\n', b'\n')),
         ([schema], [], []))
    case('описание макета СКД - СКД', lambda d: edit_file(d, 'CommonTemplates/ОбщийМакет1.xml', '<CommonTemplate/>', '<CommonTemplate></CommonTemplate>'),
         (['CommonTemplates/ОбщийМакет1.xml'], [], []))
    case('макет не СКД - окружение', lambda d: edit_file(d, 'Reports/Отчет1/Templates/Печать/Ext/Template.xml', '/>', '></document>'),
         ([], ['Reports/Отчет1/Templates/Печать/Ext/Template.xml'], []))
    case('нет файла схемы в выгрузке - СКД', lambda d: (d / schema).unlink(), ([schema], [], []))
    case('лишний файл окружения в выгрузке', lambda d: write_tree_add(d, 'Catalogs/Лишний.xml', '<x/>'),
         ([], ['Catalogs/Лишний.xml'], []))

    refs = dcs_env_refs(ours)
    t.check('ссылки синтетики найдены', refs['ok'] and refs['refs'] == 2 and refs['values'] == 2, refs)
    write_tree(theirs, SYNTHETIC_TREE)
    edit_file(theirs, schema, 'CatalogRef.Валюты', 'CatalogRef.НетТакого')
    edit_file(theirs, 'CommonTemplates/ОбщийМакет1/Ext/Template.xml', 'Статусы.Новый', 'Статусы.НетТакого')
    refs = dcs_env_refs(theirs)
    t.check('ссылки вне окружения - отказ', not refs['ok'] and sorted(m['ref'] for m in refs['missing']) == [
        'cfg:CatalogRef.НетТакого', 'Перечисление.Статусы.НетТакого'], refs)

    good = {'tool': 'roundtrip.py', 'manifestVersion': MANIFEST_VERSION, 'format': '2.20',
            'jar': {'name': 'j.jar', 'sha256': '0' * 64},
            'scenarios': {'dcs': {'main': 'cf', 'selfCheck': {'strictOk': True, 'envRefsOk': True}, 'dcsFiles': [],
                                  'grid': []}},
            'files': tree_hashes_under(ours, 'cf')}
    root = tmp / 'syn-manifest'
    write_tree(root / 'cf', SYNTHETIC_TREE)
    t.check('манифест синтетики без замечаний', manifest_problems(good, root) == [], manifest_problems(good, root))
    bad = json.loads(json.dumps(good))
    bad['format'] = '220'
    bad['scenarios']['dcs']['selfCheck']['strictOk'] = False
    edit_file(root / 'cf', 'Catalogs/Валюты.xml', 'Валюты<', 'Валюта<')
    write_tree_add(root, 'cf/Лишний.xml', '<x/>')
    problems = manifest_problems(bad, root)
    t.check('манифест: формат, самопроверка, изменённый и лишний файл', len(problems) == 4, problems)

    def self_check_problems(empty_ok):
        variant = json.loads(json.dumps(good))
        variant['scenarios']['dcs']['selfCheck']['emptySchemaOk'] = empty_ok
        return [p for p in manifest_problems(variant, root) if 'самопроверка' in p]
    t.check('манифест: пустая схема не совпала с эталоном - замечание; совпала или эталона рядом нет - нет',
            self_check_problems(False) and not self_check_problems(True) and not self_check_problems(None))
    self_test_grid_rules(t, tmp)


# исходник DcsSettingsEdit: с ним сверяются таблица рангов, запреты параметров вывода и типы диаграмм
RANKS_SOURCE = REPO_ROOT / 'src/main/java/io/github/yellowhammer/designerxml/cf/DcsSettingsEdit.java'


def java_output_ranks(path):
    """Таблица OUTPUT_PARAMETER_RANKS из исходника DcsSettingsEdit: группы имён по порядку или None."""
    if not path.is_file():
        return None
    text = path.read_text(encoding='utf-8')
    m = re.search(r'OUTPUT_PARAMETER_RANKS\s*=\s*ranks\(List\.of\((.*?)\)\);', text, re.S)
    if not m:
        return None
    return tuple(tuple(re.findall(r'"([^"]+)"', group)) for group in re.findall(r'List\.of\(([^()]*)\)', m.group(1)))


def java_forbidden(path, name='ROOT_FORBIDDEN_OUTPUT_PARAMETERS'):
    """Набор DcsSettingsEdit.<name> (ROOT_, GROUP_, TABLE_, TABLE_GROUP_, CHART_ или CHART_GROUP_FORBIDDEN_OUTPUT_PARAMETERS)
    из исходника или None. Имя - целым словом: GROUP_… не находится внутри TABLE_GROUP_… и CHART_GROUP_…"""
    if not path.is_file():
        return None
    m = re.search(r'\b%s\s*=\s*Set\.of\((.*?)\);' % name, path.read_text(encoding='utf-8'), re.S)
    return set(re.findall(r'"([^"]+)"', m.group(1))) if m else None


def java_root_forbidden(path):
    """Набор DcsSettingsEdit.ROOT_FORBIDDEN_OUTPUT_PARAMETERS из исходника или None."""
    return java_forbidden(path, 'ROOT_FORBIDDEN_OUTPUT_PARAMETERS')


def java_group_forbidden(path):
    """Набор DcsSettingsEdit.GROUP_FORBIDDEN_OUTPUT_PARAMETERS из исходника или None."""
    return java_forbidden(path, 'GROUP_FORBIDDEN_OUTPUT_PARAMETERS')


# Мутации md-sparrow для схем: исходник канала apply-mutation - чтобы сетка не отставала от операций (новая мутация
# без элемента сетки - сбой самотеста). Чтения (info, validate, settings-info, type-dictionary) - не мутации
MUTATIONS_SOURCE = REPO_ROOT / 'src/main/java/io/github/yellowhammer/designerxml/cli/ApplyMutationCmd.java'
DCS_READ_OPS = ('cf-dcs-info', 'cf-dcs-validate', 'cf-dcs-settings-info', 'cf-dcs-type-dictionary')
# Элементы сетки, которые после себя не оставляют строк: создают и удаляют (проверяют, что следа нет)
GRID_NET_ZERO = ('удаление-макета',)
XSD_DATA_UI = XSD_ROOT / 'schemas' / 'designer' / '2.20' / 'v8.1c.ru-8.1-data-ui.xsd'


def java_chart_types(path):
    """Список DcsSettingsEdit.CHART_TYPES из исходника или None."""
    if not path.is_file():
        return None
    m = re.search(r'CHART_TYPES\s*=\s*List\.of\((.*?)\);', path.read_text(encoding='utf-8'), re.S)
    return tuple(re.findall(r'"([^"]+)"', m.group(1))) if m else None


def xsd_chart_types(path):
    """Перечисление ChartType из XSD data-ui или None."""
    if not path.is_file():
        return None
    xs = '{http://www.w3.org/2001/XMLSchema}'
    for simple in ET.parse(path).getroot().iter(xs + 'simpleType'):
        if simple.get('name') == 'ChartType':
            return tuple(e.get('value') for e in simple.iter(xs + 'enumeration'))
    return None


def java_mutations(path):
    """Имена мутаций cf-dcs-* в исходнике канала или None."""
    if not path.is_file():
        return None
    return sorted(set(re.findall(r'"(cf-dcs-[a-z-]+)"', path.read_text(encoding='utf-8'))) - set(DCS_READ_OPS))


def grid_targets(entries):
    """@ключи путей, на которые ссылаются шаги сетки."""
    out = set()
    for entry in entries:
        for s in entry['steps']:
            out.update(v[1:] for v in s.values() if isinstance(v, str) and v.startswith('@'))
    return out


def self_test_grid_rules(t, tmp):
    """Основная сетка без java: таблица, 47 типов диаграмм против XSD, md-sparrow и мастера, уровни и запреты
    параметров вывода против md-sparrow, входы редактора, покрытие мутаций, «ожидаемое» и сверка по элементам на
    синтетике."""
    log('--- самотест: основная сетка dcs')
    ids = [e['id'] for e in DCS_GRID]
    t.check('сетка: id уникальны, группы 1-4, у каждого элемента шаги (%d элементов таблицы)' % len(ids),
            len(set(ids)) == len(ids) and {e['group'] for e in DCS_GRID} == {1, 2, 3, 4}
            and all(e['steps'] and all(s.get('op') for s in e['steps']) for e in DCS_GRID))
    t.check('сетка: пути шагов - из DCS_PATHS', grid_targets(DCS_GRID) <= set(DCS_PATHS),
            grid_targets(DCS_GRID) - set(DCS_PATHS))
    t.check('типы диаграмм: 47 разных лексем', len(CHART_TYPES) == 47 and len(set(CHART_TYPES)) == 47)
    xsd = xsd_chart_types(XSD_DATA_UI)
    if xsd is None:
        log('  --   XSD data-ui рядом нет (%s): сверка типов диаграмм с XSD пропущена' % XSD_DATA_UI)
    else:
        t.check('типы диаграмм совпадают с перечислением ChartType XSD data-ui 2.20 (и порядком)', xsd == CHART_TYPES, xsd)
    java = java_chart_types(RANKS_SOURCE)
    if java is None:
        log('  --   DcsSettingsEdit рядом нет: сверка типов диаграмм с md-sparrow пропущена')
    else:
        t.check('типы диаграмм совпадают с DcsSettingsEdit.CHART_TYPES', java == CHART_TYPES, java)
    charts = next(e for e in DCS_GRID if e['id'] == 'типы-диаграмм')
    lexemes = [s['payload']['value']['text'] for s in charts['steps'] if s['op'] == 'cf-dcs-set-parameter-value']
    t.check('вариант «Все типы диаграмм»: каждая лексема ровно у одной диаграммы',
            lexemes == list(CHART_TYPES) and sum(1 for s in charts['steps'] if s['op'] == 'cf-dcs-add-structure-item') == 47)
    t.check('уровень узла по виду и шагам пути - как DcsSettingsEdit.OutputLevel (корень, группировка, таблица, группа '
            'таблицы, диаграмма, группа диаграммы); вложенная в группу таблицы или диаграммы группа - уровень верхней '
            'группы',
            output_level('settings', []) == 'root' and output_level('group', ['structure']) == 'group'
            and output_level('group', ['structure', 'structure']) == 'group'
            and output_level('table', ['structure']) == 'table' and output_level('chart', ['structure']) == 'chart'
            and output_level('group', ['structure', 'rows']) == 'table-group'
            and output_level('group', ['structure', 'columns']) == 'table-group'
            and output_level('group', ['structure', 'rows', 'structure']) == 'table-group'
            and output_level('group', ['structure', 'points']) == 'chart-group'
            and output_level('group', ['structure', 'series']) == 'chart-group'
            and output_level('group', ['structure', 'rows', 'structure', 'points']) == 'chart-group'
            and output_level('table', ['structure', 'structure']) == 'table')
    t.check('запреты таблицы (10), группы таблицы (7), диаграммы (19 - все, кроме ТипДиаграммы, ВыводитьЗаголовок, '
            'Заголовок) и группы диаграммы (20 - все, кроме КоличествоЗаписей, ПроцентЗаписей) - имена таблицы рангов',
            all(set(names) <= set(OUTPUT_NAMES) and len(set(names)) == len(names)
                for names in LEVEL_FORBIDDEN_OUTPUT.values())
            and [len(LEVEL_FORBIDDEN_OUTPUT[k]) for k in ('table', 'table-group', 'chart', 'chart-group')] == [10, 7, 19, 20]
            and set(OUTPUT_NAMES) - set(CHART_FORBIDDEN_OUTPUT) == {'ТипДиаграммы', 'ВыводитьЗаголовок', 'Заголовок'}
            and set(OUTPUT_NAMES) - set(CHART_GROUP_FORBIDDEN_OUTPUT) == {'КоличествоЗаписей', 'ПроцентЗаписей'}
            and set(OUTPUT_NAMES) - set(TABLE_GROUP_FORBIDDEN_OUTPUT) >= {'РасположениеОбщихИтогов'}
            and not any('РасположениеОбщихИтогов' not in LEVEL_FORBIDDEN_OUTPUT[k] for k in LEVEL_FORBIDDEN_OUTPUT
                        if k != 'table-group'))
    pairs = [g for g in OUTPUT_RANKS if len(g) == 2]
    t.check('пары «=» таблицы рангов вместе не хранятся ни на одном из шести уровней: в каждой паре на каждом уровне '
            'хотя бы одно имя запрещено',
            len(pairs) == 2 and all(set(pair) & set(forbidden) for pair in pairs
                                    for forbidden in LEVEL_FORBIDDEN_OUTPUT.values())
            and len(LEVEL_FORBIDDEN_OUTPUT) == 6, pairs)
    t.check('пять параметров, которых платформа не хранит в корне, и пять, которых она не хранит у группировки, - имена '
            'таблицы рангов; общий у них один - РасположениеОбщихИтогов',
            set(ROOT_FORBIDDEN_OUTPUT) <= set(OUTPUT_NAMES) and set(GROUP_FORBIDDEN_OUTPUT) <= set(OUTPUT_NAMES)
            and len(set(ROOT_FORBIDDEN_OUTPUT)) == len(set(GROUP_FORBIDDEN_OUTPUT)) == 5
            and set(ROOT_FORBIDDEN_OUTPUT) & set(GROUP_FORBIDDEN_OUTPUT) == {'РасположениеОбщихИтогов'})
    java = java_output_ranks(RANKS_SOURCE)
    if java is None:
        log('  --   таблица рангов md-sparrow не найдена рядом (%s): сверка пропущена' % RANKS_SOURCE)
    else:
        t.check('таблица рангов совпадает с DcsSettingsEdit.OUTPUT_PARAMETER_RANKS', java == OUTPUT_RANKS, java)
        forbidden = java_root_forbidden(RANKS_SOURCE)
        t.check('пять параметров, которых платформа не хранит в корне, совпадают с '
                'DcsSettingsEdit.ROOT_FORBIDDEN_OUTPUT_PARAMETERS', forbidden == set(ROOT_FORBIDDEN_OUTPUT), forbidden)
        forbidden = java_group_forbidden(RANKS_SOURCE)
        t.check('пять параметров, которых платформа не хранит у группировки структуры, совпадают с '
                'DcsSettingsEdit.GROUP_FORBIDDEN_OUTPUT_PARAMETERS', forbidden == set(GROUP_FORBIDDEN_OUTPUT), forbidden)
        for level, constant in LEVEL_CONSTANTS.items():
            forbidden = java_forbidden(RANKS_SOURCE, constant)
            t.check('параметры, которых платформа не хранит %s (%d), совпадают с DcsSettingsEdit.%s' % (
                LEVEL_TITLES[level], len(LEVEL_FORBIDDEN_OUTPUT[level]), constant),
                forbidden == set(LEVEL_FORBIDDEN_OUTPUT[level]), forbidden)

    inputs = None
    if not DCS_INPUTS.is_file():
        log('  --   входов сетки рядом нет (%s): проверки входов пропущены' % DCS_INPUTS)
    else:
        inputs = load_dcs_inputs(DCS_INPUTS)
        t.check('входы: тексты конструктора и пакеты мастера без замечаний (%d запросов, %d пакетов)' % (
            len(inputs['queries']), len(inputs['wizard'])), not dcs_inputs_problems(inputs)
            and len(inputs['queries']) >= 6 and {w['kind'] for w in inputs['wizard']} == {'list', 'table', 'chart'})
        t.check('типы диаграмм совпадают со списком мастера настроек расширения (chartTypes входов)',
                sorted(inputs.get('chartTypes') or []) == sorted(CHART_TYPES), inputs.get('chartTypes'))
        broken = json.loads(json.dumps(inputs))
        broken['queries'][0]['text'] += '\nОБЪЕДИНИТЬ ВСЕ\nВЫБРАТЬ Т.Ссылка ИЗ Справочник.НетТакого КАК Т'
        broken['queries'][1]['dataSet'] = broken['queries'][0]['dataSet'].upper()
        broken['wizard'][0]['operations'].append({'op': 'cf-dcs-set-filter', 'variant': broken['wizard'][0]['variant'],
                                                  'payload': {'path': [], 'items': []}})
        problems = dcs_inputs_problems(broken)
        t.check('входы, отрицательный контроль: таблица вне окружения, повтор имени набора, операция вне каталога',
                len(problems) == 3 and 'Справочник.НетТакого' in ' '.join(problems), problems)
        entries = dcs_grid(inputs)
        gids = [e['id'] for e in entries]
        declared = {s['payload']['name'].lower() for e in entries for s in e['steps']
                    if s['op'] == 'cf-dcs-add-parameter' and s['objectXml'] == '@queries'}
        used = {p.lower() for q in inputs['queries'] for p in q['parameters']}
        t.check('сетка со входами: id уникальны (%d элементов), пути - из DCS_PATHS' % len(gids),
                len(set(gids)) == len(gids) and grid_targets(entries) <= set(DCS_PATHS))
        t.check('конструктор: каждый &-параметр текстов объявлен один раз', used == declared and sum(
            1 for e in entries for s in e['steps'] if s['op'] == 'cf-dcs-add-parameter' and s['objectXml'] == '@queries')
            == len(declared), (used, declared))
        ops = {s['op'] for e in entries for s in e['steps']}
        known = java_mutations(MUTATIONS_SOURCE)
        if known is None:
            log('  --   канал apply-mutation рядом нет (%s): покрытие мутаций не сверяется' % MUTATIONS_SOURCE)
        else:
            t.check('сетка покрывает все мутации cf-dcs-* md-sparrow (%d)' % len(known), set(known) <= ops,
                    sorted(set(known) - ops))

    # «ожидаемое» и сверка по элементам на синтетике
    before = {'s.xml': ['<a>1</a>', '<b>2</b>']}
    after = {'s.xml': ['<a>1</a>', '<c x="1">', '<b>3</b>', 'продолжение текста'], 'n.xml': ['<n>н</n>']}
    added = grid_added(before, after)
    t.check('ожидаемое: вставленные и заменённые строки по файлам', added == {
        's.xml': ['<c x="1">', '<b>3</b>', 'продолжение текста'], 'n.xml': ['<n>н</n>']}, added)
    t.check('ожидаемое: в итог - только дожившие строки', grid_surviving(added, {'s.xml': ['<a>1</a>', '<b>3</b>']})
            == {'s.xml': ['<b>3</b>']})
    t.check('строки с содержимым: голые теги не в счёт, атрибут, текст и продолжение - в счёт',
            grid_lines('﻿<r>\r\n\t<a>\r\n\t\t<b>x</b>\r\n\t\tтекст\r\n\t</a>\r\n\t<c d="1"/>\r\n</r>'.encode('utf-8'))
            == ['<b>x</b>', 'текст', '<c d="1"/>'])
    root = tmp / 'grid-syn'
    write_tree(root, {'s.xml': '<a>1</a>\r\n<b>3</b>\r\n<b>3</b>\r\n'})
    records = [{'id': 'один', 'expected': {'s.xml': ['<b>3</b>', '<b>3</b>']}},
               {'id': 'два', 'expected': {'s.xml': ['<b>3</b>', '<b>3</b>', '<b>3</b>']}},
               {'id': 'три', 'expected': {'нет.xml': ['<x>1</x>']}}]
    changed = grid_check(records, root)
    t.check('сверка по элементам: число повторов и пропавший файл', [c['id'] for c in changed] == ['два', 'три']
            and changed[0]['missing'] == ['<b>3</b>'], changed)
    if EMPTY_SCHEMA_ETALON.is_file():
        write_tree(root, {DCS_PATHS['empty']: EMPTY_SCHEMA_ETALON.read_bytes()})
        same = empty_schema_check(root)
        edit_file(root, DCS_PATHS['empty'], 'Основной', 'Иной')
        t.check('эталон пустой схемы: те же байты - ok, другие - нет', same['ok'] is True
                and empty_schema_check(root)['ok'] is False)
    else:
        log('  --   эталона пустой схемы рядом нет (%s): сверка пропущена' % EMPTY_SCHEMA_ETALON)


def self_test_grid_prepared(t, root, section, tmp):
    """Сетка подготовленного дерева: «ожидаемое» в манифесте, сверка с собой, имитация изменений платформы."""
    log('--- самотест: основная сетка подготовленного дерева')
    records = section.get('grid') or []
    cf = root / section['main']
    t.check('манифест: сетка групп 1-4 записана (%d элементов)' % len(records),
            records and {r['group'] for r in records} == {1, 2, 3, 4})
    empty = [r['id'] for r in records if not r['expected'] and r['id'] not in GRID_NET_ZERO]
    t.check('у каждого элемента есть ожидаемые строки (кроме создания-и-удаления)', not empty, empty)
    t.check('сверка подготовленного с «ожидаемым»: все элементы на месте', grid_check(records, cf) == [],
            grid_check(records, cf)[:3])
    schemas = sorted(dcs_layout([cf])[0])
    unkept = {rel: unkept_written((cf / rel).read_bytes()) for rel in schemas}
    t.check('сетка и входы редактора пишут только хранимое: ни одного параметра вывода, запрещённого на уровне узла '
            '(корень, группировка, таблица, диаграмма и их группы), и ни одного флага списка у связи без parameter '
            '(%d схем)' % len(schemas), schemas and not any(unkept.values()),
            {rel: found for rel, found in unkept.items() if found})
    check = section.get('selfCheck') or {}
    if check.get('emptySchemaOk') is None:
        log('  --   эталон пустой схемы при подготовке не сверялся (рядом не было)')
    else:
        t.check('пустая схема - байты эталона платформы', check.get('emptySchemaOk') is True, check.get('emptySchema'))
    export = tmp / 'grid-export'
    copy_tree(cf, export)
    quiet = []
    t.check('имитация: выгрузка совпала - ни одного изменённого элемента, пустая схема та же',
            dcs_expected_report(section, export, quiet.append) == {
                'grid': {'entries': len(records), 'changedIds': [], 'changed': []}, 'emptySchema': 'same'}
            if check.get('emptySchema', {}).get('sha256') else not grid_check(records, export))
    edit_file(export, DCS_PATHS['common'], '>Waterfall<', '>Column<')
    edit_file(export, DCS_PATHS['main'], '<dcscor:value xsi:type="v8ui:ChartType">Pie3D</dcscor:value>',
              '<dcscor:value xsi:type="v8ui:ChartType">Pie</dcscor:value>')
    report = dcs_expected_report(section, export, quiet.append)
    t.check('имитация: платформа сменила две лексемы - названы ровно их элементы',
            report['grid']['changedIds'] == ['типы-диаграмм', 'мастер-МастерДиаграмма']
            and 'Waterfall' in report['grid']['changed'][0]['missing'][0], report['grid'])
    if check.get('emptySchema', {}).get('sha256'):
        edit_file(export, DCS_PATHS['empty'], 'Основной', 'Основной1')
        report = dcs_expected_report(section, export, quiet.append)
        t.check('имитация: пустая схема выгружена иначе - видно (и элемент её создания)',
                report.get('emptySchema') == 'differs' and 'макеты' in report['grid']['changedIds'], report)


def write_tree_add(root, rel, text):
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding='utf-8')


def tree_hashes_under(root, prefix):
    return {prefix + '/' + p.relative_to(root).as_posix(): sha256(p) for p in sorted(root.rglob('*')) if p.is_file()}


def self_test_prepared(t, root, tmp):
    """Подготовленное дерево: манифест, целость, ссылки, сверка с самим собой и с имитацией выгрузки."""
    log('--- самотест: подготовленное %s' % root)
    manifest = load_manifest(root)
    problems = manifest_problems(manifest, root)
    t.check('манифест и целость файлов', not problems, problems)
    section = (manifest.get('scenarios') or {}).get('dcs')
    if not isinstance(section, dict):
        t.check('сценарий dcs подготовлен', False)
        return
    cf = root / section['main']
    refs = dcs_env_refs(cf)
    t.check('ссылки схем на окружение (%d типов, %d значений, %d TypeId)' % (refs['refs'], refs['values'], refs['typeIds']),
            refs['ok'] and refs['refs'] > 0, refs['missing'])
    strict = section.get('selfCheck', {}).get('strict') or []
    t.check('строгая проверка всех схем (%d)' % len(strict), strict and all(s['ok'] for s in strict), strict)
    quiet = []
    result = compare_dcs('с самим собой', cf, cf, say=quiet.append)
    t.check('сверка с самим собой: 0 различий', result[0] == len(files_of(cf)) and result[1:] == ([], [], []), result)
    # Имитация выгрузки: копия с переставленными типами, сведениями о выгрузке и правкой окружения - как
    # правила сверки ведут себя на настоящих байтах md-sparrow
    export = tmp / 'export-sim'
    shutil.rmtree(export, ignore_errors=True)
    shutil.copytree(cf, export)
    (export / DUMP_INFO).write_text('<ConfigDumpInfo/>', encoding='utf-8')
    contents, _, owners = dcs_layout([cf])
    swapped = [rel for rel in sorted(contents) if swap_lines(export, rel, TYPE_LINE)]
    env_rel = next((rel for rel in sorted(files_of(cf)) if rel.startswith('Catalogs/')), None)
    if env_rel:
        path = export / env_rel
        path.write_bytes(path.read_bytes().replace(b'</MetaDataObject>', b'</MetaDataObject>\r\n', 1))
    result = compare_dcs('имитация выгрузки', cf, export, say=quiet.append)
    t.check('имитация: порядок типов не различие (%s)' % (', '.join(swapped) or 'нет серий'),
            swapped and sorted(result[3]) == swapped and result[1] == [], result)
    t.check('имитация: окружение в envDiffs, вердикт pass',
            env_rel and result[2] == [env_rel] and dcs_status(result[1], []) == 'pass', result)
    owner = next((rel for rel in sorted(owners) if b'<MainDataCompositionSchema>' in (cf / rel).read_bytes()), None)
    t.check('у отчёта есть основная схема', owner is not None)
    if owner:
        edit_file(export, owner, '<MainDataCompositionSchema>', '<MainDataCompositionSchema>x')
        result = compare_dcs('имитация: свойство схемы', cf, export, say=quiet.append)
        t.check('имитация: свойство схемы владельца - различие СКД (%s)' % owner,
                owner in result[1] and dcs_status(result[1], []) == 'accepted-normalized', result)
    if section.get('grid'):
        self_test_grid_prepared(t, root, section, tmp)
    else:
        t.check('основная сетка подготовлена', False, 'в манифесте нет grid')
    self_test_flow(t, root, manifest, tmp, sorted(contents), env_rel)


class ImitatedPlatform(Roundtrip):
    """Имитация ibcmd для самотеста потока --prepared: «выгрузка» - копия загруженного со сведениями о выгрузке.

    mutate(каталог выгрузки, номер цикла) правит выгрузку, reject - номера циклов, где «платформа» отвергает.
    """

    def __init__(self, work, fmt, mutate=None, reject=()):
        super().__init__(None, None, work, dict(os.environ))
        self.set_format(fmt)
        self.mutate, self.reject, self.cycle = mutate, reject, 0
        self.say = lambda *parts: None

    def load_cf(self, name, cf):
        self.cycle += 1
        if self.cycle in self.reject:
            return None
        out = self.work / (name + '-out')
        shutil.rmtree(out, ignore_errors=True)
        shutil.copytree(cf, out)
        (out / DUMP_INFO).write_text('<ConfigDumpInfo/>', encoding='utf-8')
        if self.mutate:
            self.mutate(out, self.cycle)
        return out


def self_test_flow(t, root, manifest, tmp, schemas, env_rel):
    """Поток --prepared (dcs_verify) на имитации платформы: вердикты, envDiffs и typeOrder в результате."""
    main_schema = next((rel for rel in schemas if rel.startswith('Reports/')), schemas[0])

    def on_cycle(cycle, change):
        return lambda out, n: change(out) if n == cycle else None

    def swap(out):
        swap_lines(out, main_schema, TYPE_LINE)

    def schema_edit(out):
        path = out / main_schema
        path.write_bytes(path.read_bytes().replace(b'</DataCompositionSchema>', b'</DataCompositionSchema>\r\n', 1))

    def env_edit(out):
        path = out / env_rel
        path.write_bytes(path.read_bytes() + b'\r\n')
    cases = (
        ('выгрузка совпала', {}, 'pass', [], []),
        ('порядок типов', {'mutate': on_cycle(1, swap)}, 'pass', [], [main_schema]),
        ('окружение отличается', {'mutate': on_cycle(1, env_edit)}, 'pass', [env_rel], []),
        ('схема нормализована', {'mutate': on_cycle(1, schema_edit)}, 'accepted-normalized', [], []),
        ('второй цикл изменил схему', {'mutate': on_cycle(2, schema_edit)}, 'unstable-roundtrip', [], []),
        ('отвергнуто при загрузке', {'reject': (1,)}, 'rejected', [], []),
        ('отвергнута своя выгрузка', {'reject': (2,)}, 'unstable-roundtrip', [], []),
    )
    for name, options, status, env_diffs, type_order in cases:
        work = tmp / 'flow'
        shutil.rmtree(work, ignore_errors=True)
        work.mkdir(parents=True)
        rt = ImitatedPlatform(work, manifest['format'], **options)
        rt.dcs_verify(root, manifest)
        r = rt.results.get('dcs', {})
        got = (r.get('status'), r.get('envDiffs'), r.get('typeOrder'))
        t.check('поток --prepared: %s -> %s' % (name, status), got == (status, env_diffs, type_order), got)
    rt = ImitatedPlatform(tmp / 'flow', '2.21')
    rt.dcs_verify(root, manifest)
    t.check('поток --prepared: формат вне 2.12-2.20 -> skipped', rt.results['dcs']['status'] == 'skipped')

    def lexeme(out):
        edit_file(out, DCS_PATHS['common'], '>Waterfall<', '>Column<')
    work = tmp / 'flow'
    shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True)
    rt = ImitatedPlatform(work, manifest['format'], mutate=on_cycle(1, lexeme))
    rt.dcs_verify(root, manifest)
    r = rt.results.get('dcs', {})
    t.check('поток --prepared: платформа сменила лексему -> accepted-normalized, в grid назван элемент сетки',
            r.get('status') == 'accepted-normalized' and (r.get('grid') or {}).get('changedIds') == [
                'типы-диаграмм'], (r.get('status'), r.get('grid', {}).get('changedIds')))


XML_NS = {
    's': 'http://v8.1c.ru/8.1/data-composition-system/schema',
    'set': 'http://v8.1c.ru/8.1/data-composition-system/settings',
    'cor': 'http://v8.1c.ru/8.1/data-composition-system/core',
}
XSI_TYPE = '{http://www.w3.org/2001/XMLSchema-instance}type'
# Списки узла настроек и теги их элементов, как шаги path md-sparrow (node()): structure - элементы структуры
# (dcsset:item), rows, columns - группы строк и колонок таблицы, points, series - группы точек и серий диаграммы
SETTINGS_LISTS = {'structure': 'item', 'rows': 'row', 'columns': 'column', 'points': 'point', 'series': 'series'}
STRUCTURE_KINDS = {'StructureItemGroup': 'group', 'StructureItemTable': 'table', 'StructureItemChart': 'chart'}


def unkept_written(data):
    """Что в схеме (текст или байты) записано вопреки отказам md-sparrow: параметры вывода, запрещённые на уровне
    узла (LEVEL_FORBIDDEN_OUTPUT, уровень - output_level), - [(вариант, путь, уровень, имя)], и связи с флагом
    списка без parameter - [('связь', источник -> приёмник, None, 'parameterListAllowed')]. Элемент структуры без
    xsi:type (группа внутри групп таблицы и диаграммы) - группа; вложенный отчёт не проверяется."""
    root = ET.fromstring(data.encode('utf-8') if isinstance(data, str) else data.lstrip(b'\xef\xbb\xbf'))
    out = []

    def walk(el, kind, lists, variant):
        level = output_level(kind, lists)
        for item in el.findall('set:outputParameters/cor:item', XML_NS):
            name = item.findtext('cor:parameter', '', XML_NS)
            if name in LEVEL_FORBIDDEN_OUTPUT[level]:
                out.append((variant, '/'.join(lists) or 'корень', level, name))
        for lst, tag in SETTINGS_LISTS.items():
            for child in el.findall('set:' + tag, XML_NS):
                xsi = (child.get(XSI_TYPE) or '').split(':')[-1]
                child_kind = 'group' if lst != 'structure' or not xsi else STRUCTURE_KINDS.get(xsi)
                if child_kind:
                    walk(child, child_kind, lists + [lst], variant)
    for v in root.findall('s:settingsVariant', XML_NS):
        settings = v.find('set:settings', XML_NS)
        if settings is not None:
            walk(settings, 'settings', [], v.findtext('set:name', '', XML_NS))
    for link in root.findall('s:dataSetLink', XML_NS):
        if link.find('s:parameterListAllowed', XML_NS) is not None and not link.findtext('s:parameter', '', XML_NS):
            out.append(('связь', '%s -> %s' % (link.findtext('s:sourceDataSet', '', XML_NS),
                                               link.findtext('s:destinationDataSet', '', XML_NS)), None,
                        'parameterListAllowed'))
    return out


def self_test_determinism(t, rt, tmp):
    """Две подготовки в разные каталоги дают одни и те же байты (UUID scaffold md-sparrow детерминированы)."""
    log('--- самотест: повторяемость подготовки')
    roots = []
    for name in ('prep-a', 'prep-b'):
        root = tmp / name
        shutil.rmtree(root, ignore_errors=True)
        root.mkdir(parents=True)
        rt.prepare(root, ['dcs'])
        roots.append(root)
    ma, mb = (json.loads((r / MANIFEST).read_text(encoding='utf-8')) for r in roots)
    ma.pop('created', None)
    mb.pop('created', None)
    t.check('манифесты двух подготовок совпадают (кроме created)', ma == mb,
            sorted(k for k in set(ma['files']) | set(mb['files']) if ma['files'].get(k) != mb['files'].get(k)))
    return roots[0]


def self_test_main(args, prepared, rt, tmp):
    t = SelfTest()
    self_test_rules(t, tmp)
    if prepared:
        self_test_prepared(t, prepared, tmp)
    elif rt:
        first = self_test_determinism(t, rt, tmp)
        self_test_prepared(t, first, tmp)
    log('')
    log('Самотест: проверок %d, сбоев %d%s' % (t.passed + len(t.failed), len(t.failed),
                                              (': ' + '; '.join(t.failed)) if t.failed else ''))
    return 1 if t.failed else 0


# ---------- запуск ----------
def parse_scenarios(text):
    """Список сценариев через запятую -> в порядке запуска; неизвестное имя - ValueError."""
    names = [n.strip() for n in text.split(',') if n.strip()]
    unknown = [n for n in names if n not in SCENARIOS]
    if unknown:
        raise ValueError('неизвестные сценарии: %s (есть %s)' % (', '.join(unknown), ', '.join(SCENARIOS)))
    return [n for n in SCENARIOS if n in names]


def prepare_dir(path):
    """Каталог подготовки: пустой, новый или подготовленный раньше этим скриптом - стирается.

    Метка .roundtrip ставится сразу: каталог неудачной подготовки (без манифеста) следующий запуск тоже сотрёт.
    """
    if path.exists() and any(path.iterdir()) and not (path / WORK_MARKER).exists():
        try:
            ours = json.loads((path / MANIFEST).read_text(encoding='utf-8')).get('tool') == 'roundtrip.py'
        except (OSError, ValueError, AttributeError):
            ours = False
        if not ours:
            raise Failure('каталог подготовки %s не пуст и не подготовлен этим скриптом: укажите пустой или новый' % path)
    shutil.rmtree(path, ignore_errors=True)
    path.mkdir(parents=True, exist_ok=True)
    (path / WORK_MARKER).write_text('каталог подготовки tools/golden-snapshots/roundtrip.py\n', encoding='utf-8')


def work_dir(arg, default=None):
    """Рабочий каталог: чужой не стираем - только пустой, новый или оставленный прошлой проверкой."""
    if arg:
        work = Path(arg).resolve()
        if work.exists() and any(work.iterdir()) and not (work / WORK_MARKER).exists():
            raise Failure('рабочий каталог %s не пуст и не создан этой проверкой: укажите пустой или новый' % work)
        shutil.rmtree(work, ignore_errors=True)
    elif default:
        work = default
        shutil.rmtree(work, ignore_errors=True)
    else:
        work = Path(tempfile.mkdtemp(prefix='rt')).resolve()
    work.mkdir(parents=True, exist_ok=True)
    (work / WORK_MARKER).write_text('рабочий каталог tools/golden-snapshots/roundtrip.py\n', encoding='utf-8')
    return work


def check_windows_paths(java_paths, *paths):
    """В режиме windows java.exe видит только диски Windows: проверяем заранее, а не на первом вызове."""
    if java_paths != 'windows':
        return
    for path in paths:
        if path is not None:
            win_path(path)


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument('--jar', help='собранный md-sparrow (*-all.jar); не нужен с --prepared')
    ap.add_argument('--bin', default=os.environ.get('BIN'), help='каталог с ibcmd')
    ap.add_argument('--work', help='рабочий каталог (по умолчанию временный)')
    ap.add_argument('--out', default='roundtrip', help='каталог для report.txt и summary.json')
    ap.add_argument('--scenarios', help='сценарии через запятую: %s (по умолчанию все; с --prepare - dcs, '
                                        'с --prepared - подготовленные)' % ','.join(SCENARIOS))
    ap.add_argument('--prepare', metavar='DIR', help='только подготовка без платформы: дерево и manifest.json в DIR')
    ap.add_argument('--format', help='формат подготовки, например 2.20 (с --prepare и --self-test --jar)')
    ap.add_argument('--prepared', metavar='DIR', help='загрузка и сверка подготовленного, без java и --jar')
    ap.add_argument('--java', default='java', help='команда java (по умолчанию java из PATH)')
    ap.add_argument('--java-paths', choices=('native', 'windows'), default='native',
                    help='windows: java.exe Windows из WSL, пути в параметрах md-sparrow - пути Windows')
    ap.add_argument('--dcs-inputs', metavar='FILE', default=str(DCS_INPUTS),
                    help='входы основной сетки dcs от редактора схем: тексты конструктора запроса и пакеты мастера '
                         '(по умолчанию dcs-inputs.json рядом со скриптом; собирает dcs-inputs.mjs)')
    ap.add_argument('--save-exports', action='store_true',
                    help='копировать первую выгрузку dcs в OUT/exports/dcs')
    ap.add_argument('--self-test', action='store_true',
                    help='без платформы: правила сверки; с --prepared или --jar - и подготовленное дерево')
    args = ap.parse_args()
    try:
        return dispatch(args)
    except Failure as e:
        log('СБОЙ ПРОВЕРКИ:', e)
        return 2


def dispatch(args):
    if args.prepare and args.prepared:
        raise Failure('--prepare и --prepared вместе не задаются')
    try:
        scenarios = parse_scenarios(args.scenarios) if args.scenarios else None
    except ValueError as e:
        raise Failure(str(e))
    env = dict(os.environ)
    jar = Path(args.jar).resolve() if args.jar else None
    if jar and not jar.is_file():
        raise Failure('нет jar %s' % jar)
    if args.prepare:
        return prepare_main(args, scenarios, jar, env)
    if args.self_test:
        prepared = Path(args.prepared).resolve() if args.prepared else None
        work = work_dir(args.work)
        try:
            rt = None
            if not prepared and jar:
                fmt = args.format or '2.20'
                check_windows_paths(args.java_paths, jar, work)
                rt = Roundtrip(jar, None, work, env, args.java, args.java_paths)
                rt.set_format(fmt)
                rt.dcs_inputs = Path(args.dcs_inputs).resolve()
            return self_test_main(args, prepared, rt, work)
        finally:
            if not args.work:
                shutil.rmtree(work, ignore_errors=True)
    if not jar and not args.prepared:
        raise Failure('задайте --jar (или --prepared с подготовленным каталогом)')
    return platform_main(args, scenarios, jar, env)


def prepare_main(args, scenarios, jar, env):
    """--prepare: md-sparrow пишет дерево и манифест, платформа не нужна."""
    scenarios = scenarios or list(PREPARABLE)
    other = [s for s in scenarios if s not in PREPARABLE]
    if other:
        raise Failure('подготовка умеет только %s, не %s' % (', '.join(PREPARABLE), ', '.join(other)))
    if not jar:
        raise Failure('для --prepare нужен --jar')
    if not args.format or not re.match(r'^\d+\.\d+$', args.format):
        raise Failure('для --prepare нужен --format вида 2.20')
    if not dcs_format_ok(args.format):
        raise Failure('сценарий dcs готовится только в форматах 2.12-2.20, не %s' % args.format)
    root = Path(args.prepare).resolve()
    check_windows_paths(args.java_paths, jar, root, Path(args.work).resolve() if args.work else None)
    prepare_dir(root)
    # служебный рабочий каталог - внутри подготовки (на том же диске, что видит java.exe), после неё стирается
    work = work_dir(args.work, None if args.work else root / '.work')
    rt = Roundtrip(jar, None, work, env, args.java, args.java_paths, roots=[root])
    rt.set_format(args.format)
    rt.dcs_inputs = Path(args.dcs_inputs).resolve()
    try:
        manifest = rt.prepare(root, scenarios)
    finally:
        if not args.work:
            shutil.rmtree(work, ignore_errors=True)
    failed = []
    for name, section in manifest['scenarios'].items():
        check = section['selfCheck']
        log('Подготовлено %s: файлов СКД %d, строгая проверка %s, ссылки на окружение %s (%d типов, %d значений, '
            '%d TypeId)' % (name, len(section['dcsFiles']), 'ok' if check['strictOk'] else 'НЕТ',
                            'ok' if check['envRefsOk'] else 'НЕТ', check['envRefs']['refs'],
                            check['envRefs']['values'], check['envRefs']['typeIds']))
        strict = check.get('strict') or []
        log('Сетка %s: элементов %d, операций md-sparrow %d видов, схем %d (строгая проверка ok у %d), пустая схема - '
            'эталон платформы: %s' % (name, len(section.get('grid') or []), len(section.get('gridOps') or []),
                                      len(strict), sum(1 for s in strict if s['ok']),
                                      {True: 'совпала', False: 'НЕ СОВПАЛА', None: 'эталона рядом нет'}[
                                          check.get('emptySchemaOk')]))
        if not self_check_ok(check):
            failed.append(name)
    log('Каталог %s: файлов %d, вызовов md-sparrow %d, jar %s' % (
        root, len(manifest['files']), manifest['calls'], manifest['jar']['sha256'][:16]))
    if failed:
        log('СБОЙ ПОДГОТОВКИ: самопроверка не прошла у %s' % ', '.join(failed))
        return 2
    if args.self_test:
        tmp = Path(tempfile.mkdtemp(prefix='rt-self')).resolve()
        try:
            return self_test_main(args, root, None, tmp)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    return 0


def platform_main(args, scenarios, jar, env):
    """Полный режим и --prepared: загрузка в ibcmd, сверка, вердикты."""
    ibcmd = find_ibcmd(args.bin)
    if not ibcmd:
        print('ibcmd не найден, задайте --bin', file=sys.stderr)
        return 2
    prepared, manifest = None, None
    if args.prepared:
        prepared = Path(args.prepared).resolve()
        manifest = load_manifest(prepared)
        problems = manifest_problems(manifest, prepared)
        if problems:
            raise Failure('подготовленный каталог %s не годится: %s' % (prepared, '; '.join(problems[:10])))
    if scenarios is None:
        scenarios = list(manifest['scenarios']) if manifest else list(SCENARIOS)
    unprepared = [s for s in scenarios if not manifest or s not in manifest['scenarios']]
    if unprepared and not jar:
        raise Failure('сценарии %s не подготовлены: для них нужен --jar' % ', '.join(unprepared))
    # Язык процесса влияет на имена по умолчанию: проверка идёт на русском, как и эталоны
    if os.name != 'nt':
        locales = subprocess.run(['locale', '-a'], capture_output=True, text=True).stdout.lower().split()
        if 'ru_ru.utf8' in locales or 'ru_ru.utf-8' in locales:
            env['LANG'] = env['LC_ALL'] = 'ru_RU.UTF-8'
    work = work_dir(args.work)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    check_windows_paths(args.java_paths, jar, work)

    rt = Roundtrip(jar, ibcmd, work, env, args.java, args.java_paths)
    rt.dcs_inputs = Path(args.dcs_inputs).resolve()
    if args.save_exports:
        rt.save_exports = (out / 'exports').resolve()
        rt.save_exports.mkdir(parents=True, exist_ok=True)
    code = 0
    try:
        rt.detect_format()
        if manifest and manifest['format'] != rt.format:
            raise Failure('подготовлено для %s, платформа даёт %s' % (manifest['format'], rt.format))
        steps = {'forms': rt.forms, 'extension': rt.extension, 'external': rt.external, 'all-kinds': rt.all_kinds,
                 'dcs': rt.dcs}
        for name in scenarios:
            if manifest and name in manifest['scenarios']:
                rt.dcs_verify(prepared, manifest)
            else:
                steps[name]()
    except Failure as e:
        log('СБОЙ ПРОВЕРКИ:', e)
        code = 2
    finally:
        statuses = [r['status'] for r in rt.results.values()]
        if code == 0 and any(s not in ('pass', 'platform', 'skipped') for s in statuses):
            code = 1
        log('')
        log('Итог (платформа %s, формат %s):' % (rt.platform, getattr(rt, 'format', '?')))
        for name, r in rt.results.items():
            env_note = ', окружение %d' % len(r['envDiffs']) if 'envDiffs' in r else ''
            if r.get('grid'):
                env_note += ', сетка: изменено элементов %d из %d' % (len(r['grid']['changedIds']), r['grid']['entries'])
            log('  %-10s %-19s совпало %d, различий %d, нестабильно %d%s %s' % (
                name, r['status'], r['same'], len(r['diffs']), len(r['unstable']), env_note, r['note']))
        (out / 'report.txt').write_text('\n'.join(LOG) + '\n', encoding='utf-8')
        summary = {'platform': rt.platform, 'format': getattr(rt, 'format', None), 'results': rt.results}
        if manifest:
            summary['prepared'] = {'format': manifest['format'], 'jar': manifest['jar'], 'created': manifest['created']}
        (out / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=1), encoding='utf-8')
        if not args.work:
            shutil.rmtree(work, ignore_errors=True)
    return code


if __name__ == '__main__':
    sys.exit(main())
