"""Проверка загрузкой: всё, что пишет md-sparrow, платформа принимает и выгружает теми же байтами.

    python roundtrip.py --jar <md-sparrow-*-all.jar> [--bin <каталог ibcmd>] [--work <каталог>] [--out <каталог>]

Формат выгрузки берётся из выгрузки пустой базы этой платформы, md-sparrow пишет файлы в нём же.
Сценарии:

  forms      объекты десяти видов с формами: cf-form-add и cf-form-compile с основным реквизитом, общая
             форма; обычный config import с проверкой, config export
  external   внешние отчёт и обработка с формами: config import --out, config export --file (ibcmd 8.3.23+)
  all-kinds  объекты всех видов формата с дочерними узлами: язык обычным config import, остальное
             config import files --no-check (ibcmd 8.3.20+: голые регистры и планы проверку не проходят)
  extension  расширение к конфигурации сценария forms: свой справочник и заимствованный

В каждом сценарии каждый файл, записанный md-sparrow, сверяется с выгрузкой платформы байт в байт;
файл, который платформа выгрузила сверх записанного, тоже считается различием (кроме ConfigDumpInfo.xml).
Если платформа отвергла расширение, тем же путём загружается выгрузка расширения, созданного ею самой.
Отвергнута и она, и все ошибки отказа те же (ibcmd 8.3.20) - сценарий помечается platform: это
ограничение версии, а не различие. Любая другая ошибка - отказ файлов md-sparrow.
Итог - report.txt с различиями и summary.json в --out. Код выхода: 0 - всё совпало, 1 - есть различия
или платформа отвергла файлы, 2 - сбой самой проверки.

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
    def compare(self, title, ours, theirs):
        same, diffs = 0, []
        ours_files = {p.relative_to(ours).as_posix() for p in ours.rglob('*') if p.is_file()}
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
            diffs.append(rel)
            log('  РАЗЛИЧИЕ', rel)
            if da.replace(b'\r\n', b'\n') == db.replace(b'\r\n', b'\n'):
                log('    отличаются только переводы строк')
                continue
            la = da.decode('utf-8', 'replace').splitlines()
            lb = db.decode('utf-8', 'replace').splitlines()
            for line in list(difflib.unified_diff(la, lb, 'md-sparrow', 'платформа', lineterm='', n=1))[:60]:
                log('    ' + line[:240])
        log('  итог %s: совпало %d, различий %d' % (title, same, len(diffs)))
        return same, diffs

    def record(self, scenario, status, same=0, diffs=(), note=''):
        self.results[scenario] = {'status': status, 'same': same, 'diffs': list(diffs), 'note': note}

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
        self.create('forms')
        if not self.ib_ok('forms', ['config', 'import'], [str(cf)], 'config import'):
            self.record('forms', 'rejected')
            return False
        out = self.work / 'forms-out'
        if not self.ib_ok('forms', ['config', 'export'], [str(out)], 'config export'):
            raise Failure('выгрузка конфигурации forms')
        same, diffs = self.compare('forms', cf, out)
        self.record('forms', 'ok' if not diffs else 'diff', same, diffs)
        return True

    def external(self):
        log('--- external: внешние отчёт и обработка с формами')
        if tuple(int(x) for x in self.platform.split('.')[:3]) < (8, 3, 23):
            log('  пропущено: ibcmd до 8.3.23 внешние объекты не собирает')
            self.record('external', 'skipped', note='ibcmd до 8.3.23')
            return
        root = self.work / 'ext'
        root.mkdir()
        total_same, total_diffs, rejected = 0, [], False
        for name, kind, main in (('ВнешнийОтчет', 'REPORT', 'ExternalReportObject'),
                                 ('ВнешняяОбработка', 'DATA_PROCESSOR', 'ExternalDataProcessorObject')):
            self.ms({'op': 'external-artifact-add', 'artifactsRoot': str(root), 'name': name, 'kind': kind})
            obj = str(root / name / (name + '.xml'))
            self.ms({'op': 'cf-form-add', 'objectXml': obj, 'name': 'Форма'})
            payload = {'mainAttribute': {'name': 'Объект', 'type': 'cfg:%s.%s' % (main, name)}}
            self.ms({'op': 'cf-form-compile', 'objectXml': obj, 'name': 'ФормаОбъекта',
                     'payloadJson': json.dumps(payload, ensure_ascii=False)})
            binary = self.work / (name + ('.erf' if kind == 'REPORT' else '.epf'))
            if not self.ib_ok('probe', ['config', 'import'], ['--out=%s' % binary, obj], 'import --out ' + name):
                total_diffs.append(name)
                rejected = True
                continue
            out = self.work / 'ext-out' / name
            out.mkdir(parents=True)
            if not self.ib_ok('probe', ['config', 'export'], ['--file=%s' % binary, str(out)],
                              'export --file ' + name):
                total_diffs.append(name)
                rejected = True
                continue
            same, diffs = self.compare(name, root / name, out)
            total_same += same
            total_diffs += [name + '/' + d for d in diffs]
        status = 'rejected' if rejected else ('ok' if not total_diffs else 'diff')
        self.record('external', status, total_same, total_diffs)

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
        self.create('all')
        if not self.ib_ok('all', ['config', 'import'], [str(lang)], 'config import языка'):
            raise Failure('конфигурация с одним языком не загрузилась')
        files = [str(p) for p in cf.rglob('*') if p.is_file()]
        if not self.ib_ok('all', ['config', 'import', 'files'], ['--base-dir=%s' % cf, '--no-check', *files],
                          'import files --no-check'):
            self.record('all-kinds', 'rejected')
            return
        out = self.work / 'all-out'
        if not self.ib_ok('all', ['config', 'export'], [str(out)], 'config export'):
            raise Failure('выгрузка конфигурации all-kinds')
        same, diffs = self.compare('all-kinds', cf, out)
        self.record('all-kinds', 'ok' if not diffs else 'diff', same, diffs)

    def extension(self, forms_loaded):
        log('--- extension: расширение со своим и заимствованным справочником')
        if not forms_loaded:
            log('  пропущено: основная конфигурация сценария forms не загрузилась')
            self.record('extension', 'skipped', note='нет основной конфигурации')
            return
        main = self.work / 'forms'
        cfe = self.work / 'cfe'
        self.ms({'op': 'init-empty-cfe', 'targetCfeRoot': str(cfe), 'name': 'Расширение', 'namePrefix': 'расш_',
                 'mainConfigurationXml': str(main / 'Configuration.xml')})
        conf = str(cfe / 'Configuration.xml')
        self.ms({'op': 'add-md-object', 'configurationXml': conf, 'type': 'CATALOG', 'name': 'расш_Справочник'})
        self.ms({'op': 'cfe-borrow-object', 'objectXml': str(main / 'Catalogs' / 'Спр.xml'), 'configurationXml': conf})
        rc, out = self.ib('forms', ['config', 'import'], ['--extension=Расширение', str(cfe)])
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
        exported = self.work / 'cfe-out'
        if not self.ib_ok('forms', ['config', 'export'], ['--extension=Расширение', str(exported)],
                          'export --extension'):
            raise Failure('выгрузка расширения')
        same, diffs = self.compare('extension', cfe, exported)
        self.record('extension', 'ok' if not diffs else 'diff', same, diffs)

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
        forms_loaded = rt.forms()
        rt.extension(forms_loaded)
        rt.external()
        rt.all_kinds()
    except Failure as e:
        log('СБОЙ ПРОВЕРКИ:', e)
        code = 2
    finally:
        statuses = [r['status'] for r in rt.results.values()]
        if code == 0 and any(s in ('diff', 'rejected') for s in statuses):
            code = 1
        log('')
        log('Итог (платформа %s, формат %s):' % (rt.platform, getattr(rt, 'format', '?')))
        for name, r in rt.results.items():
            log('  %-10s %-8s совпало %d, различий %d %s' % (name, r['status'], r['same'], len(r['diffs']),
                                                            r['note']))
        (out / 'report.txt').write_text('\n'.join(LOG) + '\n', encoding='utf-8')
        (out / 'summary.json').write_text(json.dumps(
            {'platform': rt.platform, 'format': getattr(rt, 'format', None), 'results': rt.results},
            ensure_ascii=False, indent=1), encoding='utf-8')
        if not args.work:
            shutil.rmtree(work, ignore_errors=True)
    return code


if __name__ == '__main__':
    sys.exit(main())
