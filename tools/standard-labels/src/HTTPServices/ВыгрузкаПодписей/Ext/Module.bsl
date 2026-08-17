// HTTP-сервис выгрузки подписей, которые ставит сама платформа.
//
// GET /labels/dump  -> JSON с подписями стандартных реквизитов по видам объектов.
// GET /labels/probe -> диагностика: что отвечает платформа на отдельные выражения.
//
// Вариант встроенного языка у конфигурации английский: имена стандартных реквизитов нужны
// такими же, как их пишет формат выгрузки (Ref, Code, LineNumber), а не в русском варианте.
// Язык интерфейса при этом русский, поэтому подписи приходят по-русски.

Function KindCollections()

	Kinds = New Array;
	Kinds.Add("Catalog|Metadata.Catalogs");
	Kinds.Add("Document|Metadata.Documents");
	Kinds.Add("DocumentJournal|Metadata.DocumentJournals");
	Kinds.Add("ChartOfCharacteristicTypes|Metadata.ChartsOfCharacteristicTypes");
	Kinds.Add("ChartOfAccounts|Metadata.ChartsOfAccounts");
	Kinds.Add("ChartOfCalculationTypes|Metadata.ChartsOfCalculationTypes");
	Kinds.Add("InformationRegister|Metadata.InformationRegisters");
	Kinds.Add("AccumulationRegister|Metadata.AccumulationRegisters");
	Kinds.Add("AccountingRegister|Metadata.AccountingRegisters");
	Kinds.Add("CalculationRegister|Metadata.CalculationRegisters");
	Kinds.Add("BusinessProcess|Metadata.BusinessProcesses");
	Kinds.Add("Task|Metadata.Tasks");
	Kinds.Add("ExchangePlan|Metadata.ExchangePlans");
	Kinds.Add("Sequence|Metadata.Sequences");
	Kinds.Add("Constant|Metadata.Constants");
	Kinds.Add("Enum|Metadata.Enums");
	Kinds.Add("Report|Metadata.Reports");
	Kinds.Add("DataProcessor|Metadata.DataProcessors");
	Kinds.Add("FilterCriterion|Metadata.FilterCriteria");
	Kinds.Add("ExternalDataSource|Metadata.ExternalDataSources");

	Return Kinds;

EndFunction

Function ValueOrUndefined(Source, PropertyName)

	Try
		Return Eval("Source." + PropertyName);
	Except
		Return Undefined;
	EndTry;

EndFunction

// Подпись описания: синоним, если задан, иначе представление платформы.
Function LabelOf(Description)

	Try
		Return String(Description.Presentation());
	Except
		Return "";
	EndTry;

EndFunction

Procedure CollectAttributes(Target, Collection)

	If Collection = Undefined Then
		Return;
	EndIf;

	For Each Description In Collection Do
		Target.Insert(Description.Name, LabelOf(Description));
	EndDo;

EndProcedure

Procedure CollectSections(Target, Collection)

	If Collection = Undefined Then
		Return;
	EndIf;

	For Each Section In Collection Do
		Entry = Target.Get(Section.Name);
		If Entry = Undefined Then
			Entry = New Map;
			Entry.Insert("label", LabelOf(Section));
			Entry.Insert("standardAttributes", New Map);
			Target.Insert(Section.Name, Entry);
		EndIf;
		CollectAttributes(Entry["standardAttributes"], ValueOrUndefined(Section, "StandardAttributes"));
	EndDo;

EndProcedure

Function KindEntry(Storage, Kind)

	Entry = Storage.Get(Kind);
	If Entry = Undefined Then
		Entry = New Map;
		Entry.Insert("samples", New Array);
		Entry.Insert("standardAttributes", New Map);
		Entry.Insert("standardTabularSections", New Map);
		Entry.Insert("tabularSections", New Map);
		Storage.Insert(Kind, Entry);
	EndIf;
	Return Entry;

EndFunction

Function Kinds()

	Storage = New Map;

	For Each Line In KindCollections() Do
		Parts = StrSplit(Line, "|");
		Kind = Parts[0];
		Try
			Collection = Eval(Parts[1]);
		Except
			Continue;
		EndTry;

		For Each MetadataObject In Collection Do
			Entry = KindEntry(Storage, Kind);
			Entry["samples"].Add(MetadataObject.Name);
			CollectAttributes(Entry["standardAttributes"], ValueOrUndefined(MetadataObject, "StandardAttributes"));
			CollectSections(Entry["standardTabularSections"],
				ValueOrUndefined(MetadataObject, "StandardTabularSections"));
			CollectSections(Entry["tabularSections"], ValueOrUndefined(MetadataObject, "TabularSections"));
		EndDo;
	EndDo;

	Return Storage;

EndFunction

// Подписи стандартных реквизитов табличной части: у всех видов они одни и те же,
// поэтому в выгрузке лежат одним словарём, а расхождение видно в диагностике.
Function TabularSectionAttributes(Storage)

	Result = New Map;
	For Each KeyAndValue In Storage Do
		For Each Section In KeyAndValue.Value["tabularSections"] Do
			For Each Attribute In Section.Value["standardAttributes"] Do
				Result.Insert(Attribute.Key, Attribute.Value);
			EndDo;
		EndDo;
	EndDo;
	Return Result;

EndFunction

Function Dump()

	Storage = Kinds();

	Objects = New Map;
	Sections = New Map;
	For Each KeyAndValue In Storage Do
		If KeyAndValue.Value["standardAttributes"].Count() > 0 Then
			Objects.Insert(KeyAndValue.Key, KeyAndValue.Value["standardAttributes"]);
		EndIf;
		If KeyAndValue.Value["standardTabularSections"].Count() > 0 Then
			Sections.Insert(KeyAndValue.Key, KeyAndValue.Value["standardTabularSections"]);
		EndIf;
	EndDo;

	SystemInformation = New SystemInfo;

	Result = New Map;
	Result.Insert("platformVersion", String(SystemInformation.AppVersion));
	Result.Insert("objects", Objects);
	Result.Insert("standardTabularSections", Sections);
	Result.Insert("tabularSections", TabularSectionAttributes(Storage));
	Return Result;

EndFunction

Function ProbeExpressions()

	Expressions = New Array;
	Expressions.Add("Metadata.Catalogs.Справочник1.StandardCommands");
	Expressions.Add("Metadata.Catalogs.Справочник1.Commands.Count()");
	Expressions.Add("GetStandardCommands(Metadata.Catalogs.Справочник1)");
	Expressions.Add("GetForm(""Catalog.Справочник1.ListForm"")");
	Expressions.Add("Catalogs.Справочник1.GetForm(""ListForm"")");
	Expressions.Add("Catalogs.Справочник1.GetListForm()");
	Expressions.Add("Metadata.Catalogs.Справочник1.TabularSections.ТабличнаяЧасть1"
		+ ".StandardAttributes.LineNumber.Name");
	Expressions.Add("Metadata.Catalogs.Справочник1.TabularSections.ТабличнаяЧасть1"
		+ ".StandardAttributes.LineNumber.Synonym");
	Expressions.Add("Metadata.Catalogs.Справочник1.TabularSections.ТабличнаяЧасть1"
		+ ".StandardAttributes.LineNumber.Presentation()");
	Expressions.Add("Metadata.Catalogs.Справочник1.TabularSections.ТабличнаяЧасть1"
		+ ".StandardAttributes.LineNumber.ToolTip");
	Expressions.Add("Metadata.Catalogs.Справочник1.TabularSections.ТабличнаяЧасть1"
		+ ".StandardAttributes.Count()");
	Expressions.Add("Metadata.InformationRegisters.РегистрСведений2"
		+ ".StandardAttributes.LineNumber.Presentation()");
	Expressions.Add("Metadata.AccountingRegisters.РегистрБухгалтерии2.Correspondence");
	Expressions.Add("Metadata.AccountingRegisters.РегистрБухгалтерии2.StandardAttributes.Count()");
	Expressions.Add("Metadata.Sequences.Последовательность1.StandardAttributes");
	Expressions.Add("Metadata.Catalogs.Справочник1.GetStandardCommands()");
	Expressions.Add("Catalogs.Справочник1.StandardCommands");
	Expressions.Add("Metadata.Catalogs.Справочник1.CommandInterface");
	Expressions.Add("Metadata.Subsystems.Подсистема1.CommandInterface");
	Expressions.Add("Metadata.Catalogs.Справочник1.UseStandardCommands");
	Expressions.Add("Metadata.Catalogs.Справочник1.Presentation()");
	Expressions.Add("Metadata.Catalogs.Справочник1.ListPresentation");
	Return Expressions;

EndFunction

Function DescribeValue(Value)

	Try
		Return String(TypeOf(Value)) + ": " + String(Value);
	Except
		Return "?";
	EndTry;

EndFunction

Function ObjectAttributes()

	Result = New Map;
	For Each Line In KindCollections() Do
		Parts = StrSplit(Line, "|");
		Try
			Collection = Eval(Parts[1]);
		Except
			Continue;
		EndTry;
		For Each MetadataObject In Collection Do
			Attributes = New Map;
			CollectAttributes(Attributes, ValueOrUndefined(MetadataObject, "StandardAttributes"));
			Result.Insert(Parts[0] + "." + MetadataObject.Name, Attributes);
		EndDo;
	EndDo;
	Return Result;

EndFunction

Function Probes()

	Result = New Map;
	For Each Expression In ProbeExpressions() Do
		Try
			Result.Insert(Expression, "ok " + DescribeValue(Eval(Expression)));
		Except
			Result.Insert(Expression, "error " + BriefErrorDescription(ErrorInfo()));
		EndTry;
	EndDo;
	Return Result;

EndFunction

Function WriteResponse(Value)

	Writer = New JSONWriter;
	Writer.SetString(New JSONWriterSettings(JSONLineBreak.Auto, Chars.Tab));
	WriteJSON(Writer, Value);
	Body = Writer.Close();

	Response = New HTTPServiceResponse(200);
	Response.Headers.Insert("Content-Type", "application/json; charset=utf-8");
	Response.SetBodyFromString(Body);
	Return Response;

EndFunction

Function LabelsGET(Request)

	Return WriteResponse(Dump());

EndFunction

Function ProbeGET(Request)

	Result = New Map;
	Result.Insert("expressions", Probes());
	Result.Insert("objects", ObjectAttributes());
	Return WriteResponse(Result);

EndFunction
