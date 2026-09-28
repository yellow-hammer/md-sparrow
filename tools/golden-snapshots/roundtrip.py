"""Проверка загрузкой: всё, что пишет md-sparrow, платформа принимает и выгружает теми же байтами.

    python roundtrip.py --jar <md-sparrow-*-all.jar> [--bin <каталог ibcmd>] [--work <каталог>] [--out <каталог>]

Формат выгрузки берётся из выгрузки пустой базы этой платформы, md-sparrow пишет файлы в нём же.
Сценарии:

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

Загруженное платформа проверяет (config check) и применяет к базе (config apply --force), у расширения
с --extension; ibcmd без этих команд их пропускает. Затем идёт второй цикл: первая выгрузка платформы
загружается в новую базу тем же путём, проверяется, применяется и выгружается снова.

Каждый файл, записанный md-sparrow, сверяется с первой выгрузкой байт в байт, а первая выгрузка со
второй; файл, который выгрузка дала сверх сравниваемого, тоже различие (кроме ConfigDumpInfo.xml).
Серии <v8:Type> подряд сравниваются как мультимножества: порядок типов в составном типе платформа
выбирает сама. Вердикт сценария:
  pass                 записанное md-sparrow совпало с выгрузкой, второй цикл её не изменил
  accepted-normalized  платформа приняла файлы, но выгрузила иначе; второй цикл выгрузку не изменил
  unstable-roundtrip   вторая выгрузка отличается от первой
  rejected             платформа отвергла файлы при загрузке, проверке или применении
  platform             отказ - ограничение версии (см. ниже), skipped - сценарий этой версии не по силам
Если платформа отвергла расширение, тем же путём загружается выгрузка расширения, созданного ею самой.
Отвергнута и она, и все ошибки отказа те же (ibcmd 8.3.20) - сценарий помечается platform: это
ограничение версии, а не различие. Любая другая ошибка - отказ файлов md-sparrow.
Итог - report.txt с различиями и summary.json в --out. Код выхода: 0 - у всех сценариев pass, platform
или skipped, 1 - есть другие вердикты, 2 - сбой самой проверки.

Рабочий каталог --work стирается перед проверкой, поэтому он должен быть пустым, новым или оставленным
прошлой проверкой (с меткой .roundtrip). На Windows он должен быть коротким: ibcmd не работает с длинными
путями.
"""
import argparse
import difflib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

TIMEOUT = 1800
DUMP_INFO = 'ConfigDumpInfo.xml'
# Метка рабочего каталога: только такой непустой каталог проверка стирает при повторном запуске
WORK_MARKER = '.roundtrip'

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


class Roundtrip:

    def __init__(self, jar, ibcmd, work, env):
        self.jar, self.ibcmd, self.work, self.env = jar, ibcmd, work, env
        self.data_mode = None
        self.version = None
        self.results = {}
        help_text = run([ibcmd, 'help', 'config'], env)[1] + run([ibcmd, 'help', 'infobase'], env)[1]
        self.has_no_check = '--base-dir=' in help_text and '--no-check' in help_text
        self.has_extension_create = '--name-prefix=' in help_text
        self.has_check_apply = bool(re.search(r'\bcheck\b', help_text) and re.search(r'\bapply\b', help_text))
        self.platform = run([ibcmd, '--version'], env)[1].splitlines()[0].strip()

    # ---------- md-sparrow ----------
    def ms(self, params):
        params = dict(params)
        params.setdefault('schemaVersion', self.version)
        path = self.work / 'params.json'
        path.write_text(json.dumps(params, ensure_ascii=False), encoding='utf-8')
        # вывод md-sparrow читается как UTF-8: иначе на Windows он придёт в кодировке системы
        rc, out = run(['java', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-jar', str(self.jar),
                       'apply-mutation', '--params', str(path)], self.env)
        if rc != 0:
            raise Failure('md-sparrow %s: код %s: %s' % (params['op'], rc, out[-600:]))
        return out

    def ms_read(self, params):
        """Чтение через read-json: JSON-ответ md-sparrow."""
        params = dict(params)
        params.setdefault('schemaVersion', self.version)
        path = self.work / 'params.json'
        path.write_text(json.dumps(params, ensure_ascii=False), encoding='utf-8')
        rc, out = run(['java', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-jar', str(self.jar),
                       'read-json', '--params', str(path)], self.env)
        if rc != 0:
            raise Failure('md-sparrow %s: код %s: %s' % (params['op'], rc, out[-600:]))
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
            if da.replace(b'\r\n', b'\n') == db.replace(b'\r\n', b'\n'):
                log('    отличаются только переводы строк')
                continue
            la = da.decode('utf-8', 'replace').splitlines()
            lb = db.decode('utf-8', 'replace').splitlines()
            for line in list(difflib.unified_diff(la, lb, what[0], what[1], lineterm='', n=1))[:60]:
                log('    ' + line[:240])
        log('  итог %s: совпало %d, различий %d' % (title, same, len(diffs)))
        return same, diffs

    def record(self, scenario, status, same=0, diffs=(), note='', unstable=()):
        self.results[scenario] = {'status': status, 'same': same, 'diffs': list(diffs), 'note': note,
                                  'unstable': list(unstable)}

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
        self.format = m.group(1)
        self.version = 'V' + self.format.replace('.', '_')
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


TYPE_LINE = re.compile(rb'^\s*<v8:Type(?:\s[^>]*)?>[^<]*</v8:Type>\r?$')


def type_series(data):
    """Строки файла, где каждая серия <v8:Type> подряд заменена отсортированной: порядок типов не важен."""
    lines, out, run_ = data.split(b'\n'), [], []
    for line in lines + [b'']:
        if TYPE_LINE.match(line):
            run_.append(line)
            continue
        out += sorted(run_)
        run_ = []
        out.append(line)
    return out


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


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument('--jar', required=True, help='собранный md-sparrow (*-all.jar)')
    ap.add_argument('--bin', default=os.environ.get('BIN'), help='каталог с ibcmd')
    ap.add_argument('--work', help='рабочий каталог (по умолчанию временный)')
    ap.add_argument('--out', default='roundtrip', help='каталог для report.txt и summary.json')
    args = ap.parse_args()

    ibcmd = find_ibcmd(args.bin)
    if not ibcmd:
        print('ibcmd не найден, задайте --bin', file=sys.stderr)
        return 2
    env = dict(os.environ)
    # Язык процесса влияет на имена по умолчанию: проверка идёт на русском, как и эталоны
    if os.name != 'nt':
        locales = subprocess.run(['locale', '-a'], capture_output=True, text=True).stdout.lower().split()
        if 'ru_ru.utf8' in locales or 'ru_ru.utf-8' in locales:
            env['LANG'] = env['LC_ALL'] = 'ru_RU.UTF-8'
    if args.work:
        # чужой каталог не стираем: только пустой, новый или оставленный прошлой проверкой
        work = Path(args.work)
        if work.exists() and any(work.iterdir()) and not (work / WORK_MARKER).exists():
            print('рабочий каталог %s не пуст и не создан этой проверкой: укажите пустой или новый' % work,
                  file=sys.stderr)
            return 2
        shutil.rmtree(work, ignore_errors=True)
    else:
        work = Path(tempfile.mkdtemp(prefix='rt'))
    work.mkdir(parents=True, exist_ok=True)
    (work / WORK_MARKER).write_text('рабочий каталог tools/golden-snapshots/roundtrip.py\n', encoding='utf-8')
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    rt = Roundtrip(Path(args.jar).resolve(), ibcmd, work, env)
    code = 0
    try:
        rt.detect_format()
        rt.forms()
        rt.extension()
        rt.external()
        rt.all_kinds()
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
            log('  %-10s %-19s совпало %d, различий %d, нестабильно %d %s' % (
                name, r['status'], r['same'], len(r['diffs']), len(r['unstable']), r['note']))
        (out / 'report.txt').write_text('\n'.join(LOG) + '\n', encoding='utf-8')
        (out / 'summary.json').write_text(json.dumps(
            {'platform': rt.platform, 'format': getattr(rt, 'format', None), 'results': rt.results},
            ensure_ascii=False, indent=1), encoding='utf-8')
        if not args.work:
            shutil.rmtree(work, ignore_errors=True)
    return code


if __name__ == '__main__':
    sys.exit(main())
