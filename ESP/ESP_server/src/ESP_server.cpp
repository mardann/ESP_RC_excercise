#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "DriveHAL.h"
#include <Preferences.h>
// #include <LiquidCrystal_I2C.h>
// #include <ESP32Servo.h>
// #include <Adafruit_NeoPixel.h>

#define SERVICE_UUID "6a0ff9be-1bce-46e5-9013-3b9ec78a338e"
#define DRIVE_CHARACTARISTIC_UUID "0bed3ebb-e9b0-4b4e-95f4-44677fd04f24"
#define UPLINK_CHARACTARISTIC_UUID "5b78888f-1cff-4615-9922-9924c724b489"

#define SERVO_PIN 4
#define MOTOR_IN1_PIN 16
#define MOTOR_IN2_PIN 17

#define LED_PIN 15

//reserve for future:
#define US_TRIG_PIN 32
#define US_ECHO_PIN 33
//in mm/µs units
#define SOUND_SPEED 0.343
//asuming 4m is the max range of the HC-SR04 sensor, double for to and fro, and add a buffer for 10m, it'll take 29155µs to travel that distance
#define MAX_DISTANCE_TIMEOUT 29155

#define I2C_SCL_PIN 22
#define I2C_SDA_PIN 21


// LiquidCrystal_I2C lcd(0x27, 16, 2);

int8_t x = 0;
int8_t y = 0;
int8_t x_trim = 0;

bool dataStreamStarted = false;
unsigned long lastRefreshTime = 0;
const uint16_t refreshInterval = 200;

unsigned long lastUplinkSent = 0;
const uint16_t uplinkInterval = 300;

//uplink
BLECharacteristic *pUplinkCharachtaristics = nullptr;
BLEServer *pServer = nullptr;
// forward declaration so we can hold a pointer before the class is defined
class MyServerCallback;
MyServerCallback *pServerCallback = nullptr;

bool deviceConnected = false;
unsigned long lastClientSeen = 0;
const unsigned long CONNECTION_TIMEOUT_MS = 500;


//servo control
// Servo steeringServo;

float targetAngle = 90.0;

int targetSpeed = 0;

//distance measurment
long distanceMM = 0;

DriveHAL drive(MOTOR_IN1_PIN, MOTOR_IN2_PIN, SERVO_PIN);

Preferences preferences;

void bluetoothInit();
void uplinkUpdate();
float measureDistanceMM();
void stopEverything();

class MyServerCallback : public BLEServerCallbacks {
  public:
    void onConnect(BLEServer *pServer)
    {
      // lcd.clear();
      // lcd.setCursor(0, 0);
      // lcd.print("Connected!");
      ledcWrite(0, 255);
      deviceConnected = true;
      lastClientSeen = millis();
    }

    void onDisconnect(BLEServer *pServer)
    {
      preferences.begin("car_settings", false);
      int8_t previous_x_trim = preferences.getChar("x_trim", 0);
      if (previous_x_trim != x_trim)
      {
        preferences.putChar("x_trim", x_trim);
        Serial.printf("Saving new x_trim value to storage: %d\n", x_trim);
      }
      else
      {
        Serial.println("x_trim value unchanged, not saving");
      }
      preferences.end();
      stopEverything();
      // if(pServerCallback != nullptr){
      //   pServerCallback->onDisconnect(pServer);
      // }
      deviceConnected = false;
      pServer->getAdvertising()->start();
      ledcWrite(0, 128);
    }
};



void setup() {
  Serial.begin(115200);
  Serial.println("setup called");

  preferences.begin("car_settings", false);
  x_trim = preferences.getChar("x_trim", 0);
  Serial.printf("Loaded x_trim value from storage: %d\n", x_trim);
  preferences.end();

  pinMode(US_TRIG_PIN, OUTPUT);
  pinMode(US_ECHO_PIN, INPUT);

  pinMode(LED_PIN, OUTPUT);
  ledcSetup(0, 5, 8);  // Channel 0, 5Hz, 8-bit
  ledcAttachPin(LED_PIN, 0);
  ledcWrite(0, 128);

  bluetoothInit();

  drive.begin();
}


void loop() {
  // put your main code here, to run repeatedly:  


  if (dataStreamStarted) {
    // float diff = targetAngle - smoothedAngle;
    // smoothedAngle += diff * smoothingFactor;
    // steeringServo.write((int)smoothedAngle);
    // Serial.print("Target: "); Serial.print(targetAngle);
    drive.setSteeringAngle(targetAngle - x_trim);

    drive.setSpeed(targetSpeed);
    Serial.printf("X: %d; Y: %d\n", x, y);
    
  }

  distanceMM = measureDistanceMM();
  float distanceCm = (float)distanceMM / 10;
  // Serial.printf("Distance (cm): %f\n", distanceCm);

  // connection watchdog: if we're connected but haven't received data recently,
  // stop motors and return to advertising so client can reconnect.
  if (deviceConnected && (millis() - lastClientSeen > CONNECTION_TIMEOUT_MS)) {
    Serial.println("Connection timeout — stopping everything and restarting advertising");
    stopEverything();

    if(pServerCallback != nullptr){
      pServerCallback->onDisconnect(pServer);
    }

    deviceConnected = false;
    dataStreamStarted = false;
    if (pServer != nullptr) {
     
      pServer->getAdvertising()->start();
    }

    
    ledcWrite(0, 128);
  }
  uplinkUpdate();

  delay(10);
}

void stopEverything() {
  //stop motor:
  drive.stop();
  drive.centerSteering();

  //reset params:
  x = 0;
  y = 0;
  dataStreamStarted = false;
}


class MyCharacteristicCallback : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharactaristic) {
    uint8_t *data = pCharactaristic->getData();
    if (pCharactaristic->getLength() >= 3) {
      if (!dataStreamStarted) {
        dataStreamStarted = true;
      };
      x = (int8_t)data[0];
      y = (int8_t)data[1];

      int8_t client_x_trim = (int8_t)data[2];
      //128 indicates value not set from cleint, so we ignore until client updates from value from storage here 
      if(client_x_trim != x_trim && (client_x_trim != 128 && client_x_trim != -128)){
        Serial.printf("Received new x_trim value from client: %d\n", client_x_trim);
           x_trim = constrain(client_x_trim, -100, 100);
        }
    

      targetAngle = map(x, -100, 100, 125, 55);
      targetSpeed = y;
      // mark last seen time to detect unexpected disconnects
      lastClientSeen = millis();
      deviceConnected = true;
    }
  }
};




int currentSpeed = 0;

float measureDistanceMM(){

  digitalWrite(US_TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(US_TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(US_TRIG_PIN, LOW);

  long rawPulsT  = pulseIn(US_ECHO_PIN, HIGH, MAX_DISTANCE_TIMEOUT);
  // Serial.print("Raw pulse t = "); Serial.println(rawPulsT);

  return rawPulsT * SOUND_SPEED / 2;

} 

void bluetoothInit() {
  BLEDevice::init("ESP32_RC_CAR");
  pServer = BLEDevice::createServer();
  BLEService *pService = pServer->createService(SERVICE_UUID);
  pServerCallback = new MyServerCallback();
  pServer->setCallbacks(pServerCallback);

  BLECharacteristic *pDriveCharactaristic = pService->createCharacteristic(
    DRIVE_CHARACTARISTIC_UUID,
    BLECharacteristic::PROPERTY_WRITE);

  pDriveCharactaristic->setCallbacks(new MyCharacteristicCallback());

  pUplinkCharachtaristics = pService->createCharacteristic(
    UPLINK_CHARACTARISTIC_UUID,
    BLECharacteristic::PROPERTY_NOTIFY);
  pUplinkCharachtaristics->addDescriptor(new BLE2902());


  pService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();

  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);
  pAdvertising->start();
}



void uplinkUpdate() {
  if (millis() - lastUplinkSent > uplinkInterval && pUplinkCharachtaristics != nullptr) {
    lastUplinkSent = millis();
    uint8_t uplinkData[3];


   
    uplinkData[0] = (distanceMM >> 8) & 0xFF;
    uplinkData[1] = distanceMM & 0xFF;
    uplinkData[2] = static_cast<uint8_t>(x_trim + 128) & 0xFF;
    // uplinkData[3] = voltageReading & 0xFF;
    // uplinkData[4] = percentFull;

    pUplinkCharachtaristics->setValue(uplinkData, 3);
    pUplinkCharachtaristics->notify();
  }
}


// void printToScreen() {
//   unsigned long now = millis();

//   if (dataStreamStarted && now - lastRefreshTime >= refreshInterval) {
//     lastRefreshTime = now;
//     lcd.clear();
//     lcd.setCursor(0, 0);
//     lcd.print("X: ");
//     lcd.print(x);
//     lcd.setCursor(0, 1);
//     lcd.print("Y: ");
//     lcd.print(y);
//   }
// }
