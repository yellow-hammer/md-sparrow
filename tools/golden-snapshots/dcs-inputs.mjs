/**
 * Входы основной сетки сценария dcs (roundtrip.py) от редактора схем расширения: тексты запросов, собранные
 * конструктором запроса, и пакеты мастера настроек.
 *
 *     node dcs-inputs.mjs --ext <клон vscode-1c-platform-tools> [--out dcs-inputs.json] [--check]
 *
 * Зачем отдельный файл, а не тексты руками в roundtrip.py: проверка загрузкой должна видеть ровно то, что пишет
 * редактор. Поэтому тексты собирают те же модули webview, что работают в окне конструктора
 * (dcs-editor-query-model.js и dcs-editor-query-edit.js), операциями вкладок над моделью «с нуля», а пакеты мастера
 * - его планировщик (dcs-editor-settings-wizard.js, путь «В новый вариант…»). Модули исполняются в node:vm так же,
 * как в node-тестах расширения; npm-пакеты не нужны.
 *
 * Тексты ссылаются только на объекты окружения сценария (DCS_ENV в roundtrip.py: Справочник.Валюты,
 * Документ.ЗаказПокупателя с реквизитами Валюта, Сумма, Статус, Перечисление.Статусы), а поля мастера - на поля
 * набора «Заказы» основной схемы: иначе загрузка с проверкой отвергла бы конфигурацию из-за ссылки в никуда, а не
 * из-за записи md-sparrow.
 *
 * Каждый текст проходит круг конструктора: самопроверка сборки, разбор без диагностики в ту же модель. --check
 * пересобирает входы и сверяет с записанным файлом (код 1 - файл устарел), ничего не записывая.
 */
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as vm from 'node:vm';
import * as crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));

function argValue(name, fallback) {
	const index = process.argv.indexOf(name);
	return index > 0 && index + 1 < process.argv.length ? process.argv[index + 1] : fallback;
}

const EXT = argValue('--ext', process.env.DCS_EDITOR_EXT || '');
const OUT = path.resolve(argValue('--out', path.join(HERE, 'dcs-inputs.json')));
const CHECK = process.argv.includes('--check');
if (!EXT) {
	console.error('укажите --ext <клон vscode-1c-platform-tools> (или DCS_EDITOR_EXT)');
	process.exit(2);
}
const WEBVIEW = path.join(path.resolve(EXT), 'resources', 'webview');
const MODULES = ['dcs-editor-query-model.js', 'dcs-editor-query-edit.js', 'dcs-editor-settings-wizard.js'];

/** Модуль webview в текущем realm - тем же способом, что node-тесты расширения (window подменяется пустым). */
function loadModule(file) {
	const code = fs.readFileSync(path.join(WEBVIEW, file), 'utf8');
	const factory = vm.runInThisContext(`(function (module, window) {\n${code}\n})`, { filename: file });
	const shim = { exports: {} };
	factory(shim, {});
	return shim.exports;
}

const Q = loadModule('dcs-editor-query-model.js');
const E = loadModule('dcs-editor-query-edit.js').create(Q);
const W = loadModule('dcs-editor-settings-wizard.js');

const A = { statement: 0, part: 0 };

function ok(result, what) {
	if (!result || result.error) {
		throw new Error(`${what}: операция конструктора отказала: ${result && result.error}`);
	}
	return result;
}

/**
 * Текст по ОК конструктора: finalize (явный КАК у простых полей изменённых частей) и сборка; затем круг -
 * самопроверка, разбор без диагностики и в ту же модель (так проверяют node-тесты конструктора).
 */
function okText(model, initial, what) {
	const text = Q.build(E.finalize(model, initial));
	const check = Q.selfCheck(text);
	if (!check.ok) {
		throw new Error(`${what}: самопроверка сборки: ${check.message}\n${text}`);
	}
	const parsed = Q.parse(text);
	if (parsed.diagnostics.length) {
		throw new Error(`${what}: диагностика разбора: ${JSON.stringify(parsed.diagnostics)}\n${text}`);
	}
	const again = Q.build(parsed.model);
	if (again !== text) {
		throw new Error(`${what}: сборка разобранного текста дала другой текст\n${text}\n---\n${again}`);
	}
	return text;
}

/** Цепочка операций над моделью: каждая получает (модель) и возвращает результат операции конструктора. */
function chain(model, steps, what) {
	let current = model;
	for (const [label, step] of steps) {
		current = ok(step(current), `${what} / ${label}`).model;
	}
	return current;
}

// ---------- запросы: операции вкладок конструктора над моделью «с нуля» ----------
const QUERIES = [];

function query(dataSet, what, build) {
	const initial = E.newModel('');
	const model = build(initial);
	const text = okText(model, initial, dataSet);
	QUERIES.push({ dataSet, what, text, parameters: Q.parametersOf(text) });
}

// ЛЕВОЕ соединение (флажок «Все» у первой таблицы), условие с параметрами, порядок, первые N, без повторяющихся
query('ЗаказыСВалютами', 'Таблицы и поля, Связи (ЛЕВОЕ), Условия (МЕЖДУ с &), Порядок, Дополнительно (ПЕРВЫЕ, РАЗЛИЧНЫЕ)', (m) =>
	chain(m, [
		['источник', (x) => E.addSource(x, A, { table: 'Документ.ЗаказПокупателя', alias: 'Заказ' })],
		['источник 2', (x) => E.addSource(x, A, { table: 'Справочник.Валюты' })],
		['поля', (x) => E.addFields(x, A, ['Заказ.Ссылка', 'Заказ.Дата', 'Заказ.Сумма'])],
		['поле с псевдонимом', (x) => E.addField(x, A, 'Валюты.Наименование', { alias: 'ВалютаНаименование' })],
		['связь', (x) => E.addLink(x, A, { left: 'Заказ', right: 'Валюты', allLeft: true, allRight: false, condition: 'Заказ.Валюта = Валюты.Ссылка' })],
		['условие', (x) => E.addCondition(x, A, { text: 'Заказ.Дата МЕЖДУ &НачалоПериода И &КонецПериода' })],
		['порядок', (x) => E.addOrder(x, 0, 'Дата', 'desc')],
		['флаги', (x) => E.setPartFlags(x, A, { top: 100, distinct: true })],
	], 'ЗаказыСВалютами'));

// ВНУТРЕННЕЕ соединение с перечислением, простое условие «поле · = · параметр», разрешённые
query('ЗаказыПоСтатусу', 'Связи (ВНУТРЕННЕЕ), Условия (простое «=»), Дополнительно (РАЗРЕШЕННЫЕ)', (m) =>
	chain(m, [
		['источник', (x) => E.addSource(x, A, { table: 'Документ.ЗаказПокупателя', alias: 'Заказ' })],
		['источник 2', (x) => E.addSource(x, A, { table: 'Перечисление.Статусы' })],
		['поля', (x) => E.addFields(x, A, ['Заказ.Ссылка', 'Статусы.Ссылка'])],
		['связь', (x) => E.addLink(x, A, { left: 'Заказ', right: 'Статусы', allLeft: false, allRight: false, condition: 'Заказ.Статус = Статусы.Ссылка' })],
		['условие', (x) => E.addCondition(x, A, { field: 'Статусы.Ссылка', kind: '=', param: 'СтатусЗаказа' })],
		['флаги', (x) => E.setPartFlags(x, A, { allowed: true })],
	], 'ЗаказыПоСтатусу'));

// Группировка: групповое поле, суммируемые (Сумма, Количество различных), условие с агрегатом - в ИМЕЮЩИЕ
query('СуммыПоВалютам', 'Группировка (Сумма, Количество различных), Условия (агрегат - ИМЕЮЩИЕ), Порядок, Автоупорядочивание', (m) =>
	chain(m, [
		['источник', (x) => E.addSource(x, A, { table: 'Документ.ЗаказПокупателя', alias: 'Заказ' })],
		['поле', (x) => E.addField(x, A, 'Заказ.Валюта')],
		['группа', (x) => E.addGroupField(x, A, 'Заказ.Валюта')],
		['сумма', (x) => E.addSummedField(x, A, 'Заказ.Сумма', 'sum')],
		['количество', (x) => E.addSummedField(x, A, 'Заказ.Ссылка', 'countDistinct')],
		['имеющие', (x) => E.addCondition(x, A, { text: 'СУММА(Заказ.Сумма) > &Порог' })],
		['порядок', (x) => E.addOrder(x, 0, 'Валюта', 'asc')],
		['автоупорядочивание', (x) => E.setAutoOrder(x, 0, true)],
	], 'СуммыПоВалютам'));

// ОБЪЕДИНИТЬ ВСЕ: вторая часть из справочника, колонки по позициям
query('ДокументыИВалюты', 'Объединения/Псевдонимы (ОБЪЕДИНИТЬ ВСЕ, части по позициям)', (m) => {
	let x = chain(m, [
		['источник', (y) => E.addSource(y, A, { table: 'Документ.ЗаказПокупателя', alias: 'Заказ' })],
		['поля', (y) => E.addField(y, A, 'Заказ.Ссылка', { alias: 'Объект' })],
		['поле 2', (y) => E.addField(y, A, 'Заказ.Номер', { alias: 'Код' })],
	], 'ДокументыИВалюты');
	const added = ok(E.addPart(x, 0), 'ДокументыИВалюты / часть');
	const second = { statement: 0, part: added.index };
	x = chain(added.model, [
		['источник части 2', (y) => E.addSource(y, second, { table: 'Справочник.Валюты' })],
		['поля части 2', (y) => E.setFieldExpr(y, second, 0, 'Валюты.Ссылка')],
		['поле 2 части 2', (y) => E.setFieldExpr(y, second, 1, 'Валюты.Код')],
	], 'ДокументыИВалюты');
	return x;
});

// Пакет: создание ВТ с индексом, выборка из неё, уничтожение ВТ
query('ПакетСВременнойТаблицей', 'Пакет запросов (ПОМЕСТИТЬ, УНИЧТОЖИТЬ), Индекс (ИНДЕКСИРОВАТЬ ПО), Временные таблицы', (m) => {
	let x = chain(m, [
		['источник', (y) => E.addSource(y, A, { table: 'Документ.ЗаказПокупателя', alias: 'Заказ' })],
		['поля', (y) => E.addFields(y, A, ['Заказ.Ссылка', 'Заказ.Валюта', 'Заказ.Сумма'])],
		['создание ВТ', (y) => E.setStatementKind(y, 0, { kind: 'create', table: 'ВТ_Заказы' })],
		['индекс', (y) => E.addIndex(y, 0)],
		['поле индекса', (y) => E.addIndexField(y, 0, 0, 'Валюта')],
	], 'ПакетСВременнойТаблицей');
	const select = ok(E.addStatement(x, 0), 'ПакетСВременнойТаблицей / выборка');
	const s = { statement: select.index, part: 0 };
	x = chain(select.model, [
		['источник ВТ', (y) => E.addSource(y, s, { table: 'ВТ_Заказы' })],
		['поля ВТ', (y) => E.addFields(y, s, ['ВТ_Заказы.Ссылка', 'ВТ_Заказы.Сумма'])],
	], 'ПакетСВременнойТаблицей');
	const drop = ok(E.addStatement(x, select.index), 'ПакетСВременнойТаблицей / уничтожение');
	return ok(E.setStatementKind(drop.model, drop.index, { kind: 'drop', table: 'ВТ_Заказы' }), 'ПакетСВременнойТаблицей / УНИЧТОЖИТЬ').model;
});

// Компоновка данных: необязательное соединение {…}, {ВЫБРАТЬ} с «Использовать дочерние», {ГДЕ} с полем и условием
query('КомпоновкаДанных', 'Компоновка данных (необязательное соединение, {ВЫБРАТЬ} с .*, {ГДЕ} поле и условие с &)', (m) => {
	let x = chain(m, [
		['источник', (y) => E.addSource(y, A, { table: 'Документ.ЗаказПокупателя', alias: 'Заказ' })],
		['источник 2', (y) => E.addSource(y, A, { table: 'Справочник.Валюты' })],
		['поля', (y) => E.addFields(y, A, ['Заказ.Ссылка', 'Заказ.Сумма', 'Валюты.Код'])],
		['связь', (y) => E.addLink(y, A, { left: 'Заказ', right: 'Валюты', allLeft: true, allRight: false, condition: 'Заказ.Валюта = Валюты.Ссылка' })],
	], 'КомпоновкаДанных');
	const join = E.compositionTables(x.statements[0].parts[0]).find((row) => row.join);
	if (!join) {
		throw new Error('КомпоновкаДанных: нет соединения для «Обязательная»');
	}
	return chain(x, [
		['необязательное', (y) => E.setJoinRequired(y, A, join.path, false)],
		['{ВЫБРАТЬ}', (y) => E.addDcsSelect(y, 0, 'Ссылка')],
		['дочерние', (y) => E.setDcsSelect(y, 0, 0, { star: true, alias: 'Заказ' })],
		['{ВЫБРАТЬ} 2', (y) => E.addDcsSelect(y, 0, 'Сумма')],
		['{ГДЕ} поле', (y) => E.addDcsWhereField(y, A, 'Заказ.Статус')],
		['{ГДЕ} условие', (y) => E.addDcsWhereCondition(y, A, 'Заказ.Сумма >= &МинимальнаяСумма')],
	], 'КомпоновкаДанных');
});

// Текст, набранный руками в окне «Запрос…», и запрос, добавленный конструктором: неизменённый запрос пакета
// выводится исходным срезом (комментарий, строковый литерал с <, > и &, пробелы в конце строк - дословно)
{
	const typed = [
		'// набрано в окне «Запрос…»: срез выводится дословно',
		'ВЫБРАТЬ   ',
		'\tВалюты.Ссылка КАК Ссылка,',
		'\tВалюты.Код КАК Код',
		'ПОМЕСТИТЬ ВТ_Валюты',
		'ИЗ',
		'\tСправочник.Валюты КАК Валюты',
		'ГДЕ',
		'\tВалюты.Наименование <> "<&>" // литерал с разметкой  ',
		'\tИ Валюты.Код ПОДОБНО "R%"',
	].join('\n');
	const initial = Q.parse(typed).model;
	if (Q.parse(typed).diagnostics.length) {
		throw new Error('ТекстИКонструктор: набранный текст не разобран');
	}
	const added = ok(E.addStatement(initial, 0), 'ТекстИКонструктор / запрос');
	const s = { statement: added.index, part: 0 };
	const model = chain(added.model, [
		['источник ВТ', (y) => E.addSource(y, s, { table: 'ВТ_Валюты' })],
		['поля', (y) => E.addFields(y, s, ['ВТ_Валюты.Ссылка', 'ВТ_Валюты.Код'])],
	], 'ТекстИКонструктор');
	const text = okText(model, initial, 'ТекстИКонструктор');
	if (!text.startsWith(typed + '\n;')) {
		throw new Error('ТекстИКонструктор: набранный запрос пакета не вышел исходным срезом\n' + text);
	}
	QUERIES.push({
		dataSet: 'ТекстИКонструктор',
		what: 'сплайс по запросам пакета: набранный запрос с комментарием, литералом "<&>" и пробелами в конце строк + запрос конструктора',
		text,
		parameters: Q.parametersOf(text),
	});
}

// ---------- мастер настроек: три вида отчёта, каждый «В новый вариант…» ----------
const WIZARD = [];

function wizard(variant, presentation, what, state) {
	const operations = W.planWizardOperations(null, state, variant, { newVariant: true, presentation });
	if (!operations.length || operations[0].op !== 'cf-dcs-add-settings-variant' || operations[0].name !== variant) {
		throw new Error(`${variant}: пакет мастера не начинается новым вариантом`);
	}
	for (const op of operations.slice(1)) {
		if (op.variant !== variant) {
			throw new Error(`${variant}: операция ${op.op} не своего варианта`);
		}
	}
	WIZARD.push({ variant, kind: state.type, what, operations });
}

wizard('МастерСписок', 'Мастер: список', 'Список: группировки с типами иерархии, детальные записи, поля и порядок', {
	type: 'list',
	selection: ['Заказ', 'Дата', 'Сумма'],
	order: [{ kind: 'field', field: 'Дата', orderType: 'Desc' }],
	groupings: { levels: [{ field: 'Валюта', groupType: 'Hierarchy' }, { field: 'Статус', groupType: 'Items' }] },
	details: true,
});
wizard('МастерТаблица', 'Мастер: таблица', 'Таблица: группировка «Таблицы», строки и колонки', {
	type: 'table',
	selection: ['Сумма'],
	order: [],
	groupings: { tables: [{ field: 'Статус' }], rows: [{ field: 'Валюта', groupType: 'HierarchyOnly' }], columns: [{ field: 'Дата' }] },
});
wizard('МастерДиаграмма', 'Мастер: диаграмма', 'Диаграмма: точки и серии, выбор [Auto] у диаграммы, ТипДиаграммы в корне', {
	type: 'chart',
	selection: ['Сумма'],
	order: [],
	groupings: { points: [{ field: 'Валюта' }], series: [{ field: 'Статус' }] },
	chartType: 'Pie3D',
});

// ---------- итог ----------
const sources = {};
for (const file of MODULES) {
	sources[file] = crypto.createHash('sha256').update(fs.readFileSync(path.join(WEBVIEW, file))).digest('hex');
}
const result = {
	generator: 'tools/golden-snapshots/dcs-inputs.mjs',
	note: 'Собрано модулями webview редактора схем расширения: не править руками, пересобрать генератором.',
	sources,
	chartTypes: W.CHART_TYPES.map((item) => item.value),
	queries: QUERIES,
	wizard: WIZARD,
};
const text = JSON.stringify(result, null, 1) + '\n';
if (CHECK) {
	const current = fs.existsSync(OUT) ? fs.readFileSync(OUT, 'utf8') : '';
	const strip = (value) => {
		// хеши модулей меняются от любых правок расширения: сверяется содержимое входов
		const data = JSON.parse(value);
		delete data.sources;
		return JSON.stringify(data);
	};
	if (!current || strip(current) !== strip(text)) {
		console.error(`${OUT}: устарел - пересоберите: node dcs-inputs.mjs --ext <клон расширения>`);
		process.exit(1);
	}
	console.log(`${OUT}: актуален (запросов ${QUERIES.length}, пакетов мастера ${WIZARD.length})`);
} else {
	fs.writeFileSync(OUT, text, 'utf8');
	console.log(`${OUT}: запросов ${QUERIES.length}, пакетов мастера ${WIZARD.length}, типов диаграмм ${result.chartTypes.length}`);
}
