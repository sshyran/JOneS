Перем юТест;

Функция ПолучитьСписокТестов(ЮнитТестирование) Экспорт
	юТест = ЮнитТестирование;

	ВсеТесты = Новый Массив;
	ВсеТесты.Добавить("ТестДолжен_ПроверитьВерсию");
	ВсеТесты.Добавить("ТестДолжен_ПроверитьВерсию2");
	Возврат ВсеТесты;
КонецФункции

Процедура ТестДолжен_ПроверитьВерсию() Экспорт
	юТест.ПроверитьРавенство("0.1", Версия(), "Версия()");
КонецПроцедуры

Процедура ТестДолжен_ПроверитьВерсию2() Экспорт
	юТест.ПроверитьНеРавенство("0.11", Версия(), "Версия()");
КонецПроцедуры

Функция Версия() Экспорт
	Возврат "0.1";
КонецФункции