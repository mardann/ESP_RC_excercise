#include <BLEDevice.h>
#include <BLEServer.h>
#include <LiquidCrystal_I2C.h>

#define SERVICE_UUID "6a0ff9be-1bce-46e5-9013-3b9ec78a338e"
#define CHARACTARISTIC_UUID "0bed3ebb-e9b0-4b4e-95f4-44677fd04f24"

LiquidCrystal_I2C lcd(0x27, 16, 2);

int8_t x = 0;
int8_t y = 0;
bool dataStreamStarted = false;
unsigned long lastRefreshTime = 0;
const int refreshInterval = 200;

class MyCallback : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharactaristic) {
    uint8_t *data = pCharactaristic->getData();
    if (pCharactaristic->getLength() >= 2) {
      if(!dataStreamStarted){
        dataStreamStarted = true;
      };
      x = (int8_t)data[0];
      y = (int8_t)data[1];
      Serial.print("X: ");
      Serial.print(x);
      Serial.print("; Y: ");
      Serial.println(y);
    }
  }


};

void setup() {
  Serial.begin(115200);
  //lcd init
  lcd.init();
  lcd.backlight();
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Connecting...");

  BLEDevice::init("ESP32_RC_CAR");
  BLEServer *pServer = BLEDevice::createServer();
  BLEService *pService = pServer->createService(SERVICE_UUID);

  BLECharacteristic *pCharactaristic = pService->createCharacteristic(
    CHARACTARISTIC_UUID,
    BLECharacteristic::PROPERTY_WRITE);

  pCharactaristic->setCallbacks(new MyCallback());
  pService->start();

  pServer->getAdvertising()->addServiceUUID(SERVICE_UUID);
  pServer->getAdvertising()->start();
}


void loop() {
  // put your main code here, to run repeatedly:
  printToScreen();
}

void printToScreen() {
  unsigned long now = millis();

  if (dataStreamStarted && now - lastRefreshTime >= refreshInterval) {
    lastRefreshTime = now;
    lcd.clear();
    lcd.setCursor(0, 0);
    lcd.print("X: ");
    lcd.print(x);
    lcd.setCursor(0, 1);
    lcd.print("Y: ");
    lcd.print(y);
  }
};
