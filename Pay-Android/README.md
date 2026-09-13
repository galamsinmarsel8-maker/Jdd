# Кошелёк для Android

Нативный Android-прототип на Kotlin и Jetpack Compose, перенесённый из `wallet-7.1.html`.

## Возможности

- системная светлая/тёмная тема и системный язык (русский или английский);
- адаптация под планшеты: карты сеткой, экраны по центру;
- банковские карты с фото — нажатие открывает Google Wallet для оплаты в терминале;
- подарочные карты и карты постоянного клиента;
- проездные и пропуска через сканирование NFC;
- «Новая банковская карта» и «Другое» открывают соответствующие экраны Google Wallet;
- поиск по картам, заказы и уведомления;
- компоненты и векторные `CupertinoIcons` из `compose-hig`.

## Сборка

Требуются JDK 17 и Android SDK 37.

```bash
./gradlew :app:assembleDebug
```

Готовый файл: `app/build/outputs/apk/debug/app-debug.apk`.

## Google Pay

Платёжный слой вынесен в Android library-модуль `wallet-pay-sdk`. Приложение создаёт `WalletPayClient` с объектом `WalletPayConfig`, получает `PaymentsClient`, формирует запросы готовности/оплаты и отправляет выданный системой токен на backend. Модуль можно подключать в другие приложения этого проекта через `implementation(project(":wallet-pay-sdk"))`.

В интерфейсе `+` → «Добавить банковскую карту» открывает официальный защищённый экран Google Wallet через deep link `https://wallet.google.com/gw/app/addfop`. Плитка Google Wallet на главном экране запускает установленное приложение с настоящими картами пользователя. Android не предоставляет стороннему приложению номера, изображения или список банковских карт Google Wallet, поэтому приложение не показывает вымышленные реквизиты и не копирует защищённые данные.

По умолчанию debug-сборка использует `ENVIRONMENT_TEST`, шлюз `example` и не создаёт реальное списание. Для тестового backend параметры можно передать через `~/.gradle/gradle.properties` или аргументы `-P`:

```properties
GOOGLE_PAY_ENVIRONMENT=TEST
GOOGLE_PAY_MERCHANT_NAME=Pay Android
GOOGLE_PAY_GATEWAY=example
GOOGLE_PAY_GATEWAY_MERCHANT_ID=exampleGatewayMerchantId
GOOGLE_PAY_CURRENCY_CODE=USD
GOOGLE_PAY_COUNTRY_CODE=US
PAYMENT_BACKEND_URL=https://payments.example.com/google-pay/charge
```

Для реальных платежей установите `GOOGLE_PAY_ENVIRONMENT=PRODUCTION`, укажите выданный Google merchant ID, идентификаторы поддерживаемого платёжного шлюза и HTTPS endpoint:

```properties
GOOGLE_PAY_ENVIRONMENT=PRODUCTION
GOOGLE_PAY_MERCHANT_ID=01234567890123456789
GOOGLE_PAY_MERCHANT_NAME=Your Merchant Name
GOOGLE_PAY_GATEWAY=your_gateway_id
GOOGLE_PAY_GATEWAY_MERCHANT_ID=your_gateway_merchant_id
GOOGLE_PAY_CURRENCY_CODE=USD
GOOGLE_PAY_COUNTRY_CODE=US
PAYMENT_BACKEND_URL=https://payments.example.com/google-pay/charge
```

Приложение отправляет на endpoint JSON методом `POST`: `idempotencyKey`, `paymentToken`, `amount`, `currencyCode`, `description`, `cardNetwork` и последние цифры карты в `cardDetails`. Ответ с HTTP 2xx считается подтверждённым платежом. Секретный ключ платёжного шлюза должен храниться только на backend.

Production требует опубликованную release-сборку, регистрацию в Google Pay & Wallet Console, одобрение Google и обработку токена выбранным платёжным провайдером. Собственная зависимость Play Integrity в приложении не используется; обязательные проверки Google Pay и провайдера выполняются их сервисами.

## NFC-карты и метки

Откройте `+` → «Сканировать NFC-карту» и приложите карту или метку к задней части телефона. Приложение распознаёт ISO-DEP, MIFARE Classic/Ultralight, FeliCa/NFC-F, NFC-V и NDEF, показывает доступные публичные NDEF-записи и сохраняет тип карты вместе с необратимым отпечатком идентификатора.

Сканер не копирует защищённые сектора, платёжные ключи, баланс, PAN/CVV и не превращает телефон в клон банковской или транспортной карты. Для бесконтактной оплаты банковской картой требуется официальная токенизация банка/платёжной системы через Google Wallet или другой сертифицированный кошелёк. Для проверки нужен телефон с NFC; приложение также показывает понятное сообщение, если NFC отсутствует или выключен.

> PNG-файлы SF Symbols не включены: лицензия Apple не разрешает распространять их в Android-приложении. Вместо них используются доступные в `compose-hig` Compose-векторы с тем же визуальным характером.
