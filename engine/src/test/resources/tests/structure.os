﻿///////////////////////////////////////////////////////////////////////
// Тест класса Структура
///////////////////////////////////////////////////////////////////////

Перем юТест;

////////////////////////////////////////////////////////////////////
// Программный интерфейс

Функция ПолучитьСписокТестов(ЮнитТестирование) Экспорт
	
	юТест = ЮнитТестирование;
	
	ВсеТесты = Новый Массив;
	
	ВсеТесты.Добавить("ТестДолжен_СоздатьСтруктуруПоКлючамЗначениям");
	ВсеТесты.Добавить("ТестДолжен_СоздатьСтруктуруПоКлючамЗначениямСоЗначениямиПоУмолчанию");
	ВсеТесты.Добавить("ТестДолжен_ПроверитьМетодСвойство");
	//ВсеТесты.Добавить("ТестДолжен_СоздатьСтруктуруПоФиксированнойСтруктуре");
	ВсеТесты.Добавить("ТестДолжен_ПроверитьМетодОчистить");
	ВсеТесты.Добавить("ТестДолжен_ПроверитьМетодУдалить");

	ВсеТесты.Добавить("ТестДолжен_ПроверитьТипПараметровКонструктора"); // issue #888
	ВсеТесты.Добавить("ТестДолжен_СоздатьСтруктуруПоКлючамЗначениям_ПроверкаПравильностиКлючей"); // issue #674
	ВсеТесты.Добавить("ТестДолжен_СоздатьСтруктуруПоКлючамЗначениямСЛишнимиЗначениями"); // issues #674, #887
	ВсеТесты.Добавить("ТестДолжен_СоздатьСтруктуруПоКлючамЗначениямСПропускомКлючей"); // issue #674
	ВсеТесты.Добавить("ТестДолжен_ПроверитьКомпиляциюКлючевыхСловВСвойствахСтруктуры");	// issue #498
	Возврат ВсеТесты;
КонецФункции

Процедура ТестДолжен_СоздатьСтруктуруПоКлючамЗначениям() Экспорт
	Структура1 = Новый Структура("Ключ1,Ключ2", "Значение1","Значение2");
	юТест.ПроверитьРавенство( Структура1.Количество(), 2 );
	юТест.ПроверитьРавенство( Структура1.Ключ1, "Значение1" );
	юТест.ПроверитьРавенство( Структура1.Ключ2, "Значение2" );

	// issue #26
	Структура2 = Новый Структура("Ключ1, Ключ2 , Ключ3", "Значение1","Значение2","Значение3");
	юТест.ПроверитьРавенство( Структура2.Количество(), 3 );
	юТест.ПроверитьРавенство( Структура2.Ключ1, "Значение1" );
	юТест.ПроверитьРавенство( Структура2.Ключ2, "Значение2" );
	юТест.ПроверитьРавенство( Структура2.Ключ3, "Значение3" );
КонецПроцедуры

Процедура ТестДолжен_СоздатьСтруктуруПоКлючамЗначениям_ПроверкаПравильностиКлючей() Экспорт
	Структура1 = Новый Структура("ψ,ξ", "Значение1","Значение2");
	юТест.ПроверитьРавенство( Структура1.Количество(), 2 );
	юТест.ПроверитьРавенство( Структура1.ψ, "Значение1" );
	юТест.ПроверитьРавенство( Структура1.ξ, "Значение2" );

	Попытка
		Структура2 = Новый Структура("1йКлюч, 2йКлюч", "Значение1","Значение2");
	Исключение
		возврат;
	КонецПопытки;

	ВызватьИсключение "Недопустимое значение ключа Структуры";
КонецПроцедуры

Процедура ТестДолжен_ПроверитьТипПараметровКонструктора() Экспорт
	Структура1 = Новый Структура("Ключ1,Ключ2", "Значение1","Значение2");

	БезОшибки = Истина;
	Попытка
		Структура2 = Новый Структура( Структура1, "Значение1","Значение2");
	Исключение
		БезОшибки = Ложь;
	КонецПопытки;
	если БезОшибки тогда 
		ВызватьИсключение "Недопустимый тип параметра конструктора Структуры: Структура";
	конецесли;

	БезОшибки = Истина;
	Попытка
		Структура2 = Новый Структура( 1000, "Значение1","Значение2");
	Исключение
		БезОшибки = Ложь;
	КонецПопытки;
	если БезОшибки тогда 
		ВызватьИсключение "Недопустимый тип параметра конструктора Структуры: Число";
	конецесли;
	
	БезОшибки = Истина;
	Попытка
		Структура2 = Новый Структура( Неопределено, "Значение1","Значение2");
	Исключение
		БезОшибки = Ложь;
	КонецПопытки;
	если БезОшибки тогда 
		ВызватьИсключение "Недопустимый тип параметра конструктора Структуры: Неопределено";
	конецесли;
КонецПроцедуры


Процедура ТестДолжен_СоздатьСтруктуруПоКлючамЗначениямСоЗначениямиПоУмолчанию() Экспорт
	Структура1 = Новый Структура("Ключ1,Ключ2,Ключ3", "Значение1","Значение2");
	юТест.ПроверитьРавенство( Структура1.Количество(), 3 );
	юТест.ПроверитьРавенство( Структура1.Ключ1, "Значение1" );
	юТест.ПроверитьРавенство( Структура1.Ключ2, "Значение2" );
	юТест.ПроверитьРавенство( Структура1.Ключ3, Неопределено );

	Структура2 = Новый Структура("Ключ1,Ключ2,Ключ3", "Значение1",,"Значение3");
	юТест.ПроверитьРавенство( Структура2.Количество(), 3 );
	юТест.ПроверитьРавенство( Структура2.Ключ1, "Значение1" );
	юТест.ПроверитьРавенство( Структура2.Ключ2, Неопределено );
	юТест.ПроверитьРавенство( Структура2.Ключ3, "Значение3" );

	Структура3 = Новый Структура("Ключ1,Ключ2");
	юТест.ПроверитьРавенство( Структура3.Количество(), 2 );
	юТест.ПроверитьРавенство( Структура3.Ключ1, Неопределено );
	юТест.ПроверитьРавенство( Структура3.Ключ2, Неопределено );
КонецПроцедуры

Процедура ТестДолжен_СоздатьСтруктуруПоКлючамЗначениямСЛишнимиЗначениями() Экспорт
	Структура1 = Новый Структура("Ключ1,Ключ2", "Значение1","Значение2","Значение3");
	юТест.ПроверитьРавенство( Структура1.Количество(), 2 );
	юТест.ПроверитьРавенство( Структура1.Ключ1, "Значение1" );
	юТест.ПроверитьРавенство( Структура1.Ключ2, "Значение2" );

	Структура2 = Новый Структура("Ключ1,Ключ2,Ключ3",,"Значение2",,,,,,);
	юТест.ПроверитьРавенство( Структура2.Количество(), 3 );
	юТест.ПроверитьРавенство( Структура2.Ключ1, Неопределено );
	юТест.ПроверитьРавенство( Структура2.Ключ2, "Значение2" );
	юТест.ПроверитьРавенство( Структура2.Ключ3, Неопределено );
КонецПроцедуры

Процедура ТестДолжен_СоздатьСтруктуруПоКлючамЗначениямСПропускомКлючей() Экспорт
	Структура1 = Новый Структура("Ключ1,,Ключ2", "Значение1","Значение2","Значение3");
	юТест.ПроверитьРавенство( Структура1.Количество(), 2 );
	юТест.ПроверитьРавенство( Структура1.Ключ1, "Значение1" );
	юТест.ПроверитьРавенство( Структура1.Ключ2, "Значение2" );

	Структура2 = Новый Структура(" , , , Ключ1 , , , , Ключ2, ",,"Значение2");
	юТест.ПроверитьРавенство( Структура2.Количество(), 2 );
	юТест.ПроверитьРавенство( Структура2.Ключ1, Неопределено );
	юТест.ПроверитьРавенство( Структура2.Ключ2, "Значение2" );
КонецПроцедуры

Процедура ТестДолжен_ПроверитьМетодСвойство() Экспорт
	Структура = Новый Структура("Ключ1", "Значение1");

	юТест.ПроверитьРавенство( Структура.Свойство("Ключ1"), Истина );
	
	Значение = Неопределено;
	Найдено = Структура.Свойство("Ключ1", Значение);
	юТест.ПроверитьРавенство(Найдено, Истина);
	юТест.ПроверитьРавенство(Значение, "Значение1");

	юТест.ПроверитьРавенство( Структура.Свойство("НесуществующийКлюч"), Ложь );

	Найдено = Структура.Свойство("НесуществующийКлюч", Значение);
	юТест.ПроверитьРавенство(Найдено, Ложь);
	юТест.ПроверитьРавенство(Значение, Неопределено);
КонецПроцедуры

Процедура ТестДолжен_ПроверитьМетодОчистить() Экспорт
	Структура = Новый Структура("Ключ1,Ключ2", "Значение1","Значение2");
	Структура.Очистить();

	юТест.ПроверитьРавенство( Структура.Количество(), 0 );
	юТест.ПроверитьРавенство( Структура.Свойство("Ключ1"), Ложь );
КонецПроцедуры

Процедура ТестДолжен_ПроверитьМетодУдалить() Экспорт
	Структура = Новый Структура("Ключ1,Ключ2", "Значение1","Значение2");

	Структура.Удалить("Ключ1");
	юТест.ПроверитьРавенство( Структура.Количество(), 1 );
	юТест.ПроверитьРавенство( Структура.Свойство("Ключ1"), Ложь );
	юТест.ПроверитьРавенство( Структура.Свойство("Ключ2"), Истина );

	Структура.Удалить("НесуществующийКлюч");
	юТест.ПроверитьРавенство( Структура.Количество(), 1 );
	юТест.ПроверитьРавенство( Структура.Свойство("Ключ2"), Истина );
КонецПроцедуры


Процедура ТестДолжен_СоздатьСтруктуруПоФиксированнойСтруктуре() Экспорт

	ФиксированнаяСтруктура = Новый ФиксированнаяСтруктура("Ключ1, Ключ2", "Значение1", "Значение2");
	Структура = Новый Структура(ФиксированнаяСтруктура);
	
	юТест.ПроверитьРавенство(Структура.Количество(), ФиксированнаяСтруктура.Количество());
	юТест.ПроверитьРавенство(Структура.Ключ1, "Значение1");
	юТест.ПроверитьРавенство(Структура.Ключ2, "Значение2");
	
КонецПроцедуры

Процедура ТестДолжен_ПроверитьКомпиляциюКлючевыхСловВСвойствахСтруктуры() Экспорт
	 
	Структура = Новый Структура;
	Структура.Вставить("Null", Null);
	Структура.Вставить("Неопределено", Неопределено);
	Структура.Вставить("Истина", Истина);
	Структура.Вставить("Ложь", Ложь);
	Структура.Вставить("И", 1);
	Структура.Вставить("ИЛИ", 2);
	Структура.Вставить("НЕ", 3);
	
	юТест.ПроверитьРавенство(Истина, Структура.Истина, "Истина");
	юТест.ПроверитьРавенство(Ложь, Структура.Ложь, "Ложь");
	юТест.ПроверитьРавенство(Неопределено, Структура.Неопределено, "Неопределено");
	юТест.ПроверитьРавенство(Null, Структура.Null, "Null");
	юТест.ПроверитьРавенство(1, Структура.И, "И");
	юТест.ПроверитьРавенство(2, Структура.ИЛИ, "ИЛИ");
	юТест.ПроверитьРавенство(3, Структура.НЕ, "НЕ");
	
КонецПроцедуры
