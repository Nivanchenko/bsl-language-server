
#Область ПрограммныйИнтерфейс

// внутри региона, экспортная - не должно срабатывать
Процедура Хорошая() Экспорт
КонецПроцедуры

// внутри региона, неэкспортная - должно срабатывать
Процедура Плохая()
КонецПроцедуры

#КонецОбласти

#Region Public

// внутри региона, экспортная - не должно срабатывать
Процедура Good() Экспорт
КонецПроцедуры

// внутри региона, неэкспортная - должно срабатывать
Процедура Bad()
КонецПроцедуры

#Region SomeRegion
    // внутри региона, вложенная область неэкспортная - должно срабатывать
    Procedure ShouldSayFuck()
    EndProcedure
#EndRegion

#EndRegion

#Область Нестандартизованная

// внутри неспециального региона, экспортная - не должно срабатывать
Процедура ОченьХорошая() Экспорт
КонецПроцедуры

// внутри неспециального региона, неэкспортная - не должно срабатывать
Процедура ТожеНичего()
КонецПроцедуры

#КонецОбласти

#Область КакаяТоЛевая
#Область ПрограммныйИнтерфейс

// не должно сработать
Функция ВложеннаяЭкспортная()

КонецФункции

#КонецОбласти
#КонецОбласти

// вне региона, не должно срабатывать
Процедура ВнеОбласти() Экспорт
КонецПроцедуры

// вне региона, не должно срабатывать
Процедура ВнеОбластиНеЭкспортная()
КонецПроцедуры

#Область СлужебныйПрограммныйИнтерфейс

Процедура СлужебныйПрограммныйИнтерфейс() // сработает
КонецПроцедуры

&Кастом
Процедура АннотированныйМетод() // сработает
КонецПроцедуры

&Вместо
Процедура МетодВместоРасширения() // опционально
КонецПроцедуры

#КонецОбласти
