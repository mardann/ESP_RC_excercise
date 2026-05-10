#include <BLEDevice.h>
#include <BLEServer.h>
#include <LiquidCrystal_I2C.h>
#include <ESP32Servo.h>
#include <Adafruit_NeoPixel.h>

#define SERVICE_UUID "6a0ff9be-1bce-46e5-9013-3b9ec78a338e"
#define CHARACTARISTIC_UUID "0bed3ebb-e9b0-4b4e-95f4-44677fd04f24"

LiquidCrystal_I2C lcd(0x27, 16, 2);

int8_t x = 0;
int8_t y = 0;
int8_t x_trim = 0;

bool dataStreamStarted = false;
unsigned long lastRefreshTime = 0;
const int refreshInterval = 200;


//servo control
const int SERVO_PIN = 3;
Servo steeringServo;
 
float targetAngle = 90.0;


//DC motor params:
const int motorIn1 = 5;
const int motorIn2 = 4;
// const int motorEna = 27;

const int motorFreq = 500;
const int motorChannel = 1; // timer 0 is allocated to steering
const int pwmResolution = 8;

int targetSpeed = 0;

//indicaor LED

int rgbPin = 8;
Adafruit_NeoPixel grb(1, rgbPin);


void stopEverything() {
  //stop motor:
  
  ledcWrite(motorIn1, 0);
  ledcWrite(motorIn2, 0);

  //reset steering servo:
  steeringServo.write(90);

  //reset params:
  x = 0;
  y = 0;
  x_trim = 0;
  dataStreamStarted = false;

}

void setup() {
  Serial.begin(115200);
  pinMode(motorIn1, OUTPUT);
  pinMode(motorIn2, OUTPUT);
  digitalWrite(motorIn1, LOW);
  digitalWrite(motorIn2, LOW);
  //lcd init
  // lcd.init();
  // lcd.backlight();
  // lcd.clear();
  // lcd.setCursor(0, 0);
  // lcd.print("Connecting...");
  

  grb.begin();
  grb.setBrightness(100);
  grb.setPixelColor(0, 252, 94, 3);
  grb.show();

  bluetoothInit();

  // Servo setup
  ESP32PWM::allocateTimer(0);
  steeringServo.setPeriodHertz(50);
  steeringServo.attach(SERVO_PIN, 500, 2400);
  steeringServo.write(90);
  
  // Motor setup
  ledcAttach(motorIn1, motorFreq, pwmResolution);
  ledcAttach(motorIn2, motorFreq, pwmResolution);

}


void loop() {
  // put your main code here, to run repeatedly:
  // printToScreen();
  

  if(dataStreamStarted){
    // float diff = targetAngle - smoothedAngle;
    // smoothedAngle += diff * smoothingFactor;
    // steeringServo.write((int)smoothedAngle);
    // Serial.print("Target: "); Serial.print(targetAngle); 
    steeringServo.write(targetAngle + x_trim);

    motorControll(targetSpeed);

  }

  delay(10);
}


class MyCharacteristicCallback : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharactaristic) {
    uint8_t *data = pCharactaristic->getData();
    if (pCharactaristic->getLength() >= 3) {
      if(!dataStreamStarted){
        dataStreamStarted = true;
      };
      x =      (int8_t)data[0];
      y =      (int8_t)data[1];
      x_trim = (int8_t)data[2];

      targetAngle = map(x, -100, 100, 125, 55);
      targetSpeed = y;      


      Serial.print("X: ");
      Serial.print(x);
      Serial.print("; Y: ");
      Serial.println(y);
    }
  }
};

class MyServerCallback : public BLEServerCallbacks{
  void onConnect(BLEServer *pServer) {
    // lcd.clear();
    // lcd.setCursor(0, 0);
    // lcd.print("Connected!");
    
    grb.setPixelColor(0, 0, 255, 0);
    grb.show();

  }

  void onDisconnect(BLEServer *pServer) {
    // lcd.clear();
    // lcd.setCursor(0, 0);
    // lcd.print("Disconnected");
    stopEverything();
    pServer->getAdvertising()->start();
    grb.setPixelColor(0, 255, 0, 0);
    grb.show();
    

  }
};


int currentSpeed = 0;

void motorControll(int8_t setSpeed){

  // if(currentSpeed < setSpeed){
  //   int diff = abs(setSpeed - currentSpeed);
  //   int increment = min(rampValue, diff);
  //   currentSpeed =+ increment;
  // } else if(currentSpeed > setSpeed){
  //   int diff = abs(setSpeed - currentSpeed);
  //   int increment = min(diff, rampValue);
  //   currentSpeed =- increment;
  // }

  int absSpeed = abs(setSpeed);
  int pwmValue = map(absSpeed, 0, 100, 0, 200);

  if(setSpeed > 5){
    ledcWrite(motorIn1, pwmValue);
    ledcWrite(motorIn2, 0);
  } else if(setSpeed < -5){
    ledcWrite(motorIn1, 0);
    ledcWrite(motorIn2, pwmValue);
  } else {
    ledcWrite(motorIn1, 0);
    ledcWrite(motorIn2, 0);
  }

}

void bluetoothInit() {
  BLEDevice::init("ESP32_RC_CAR");
  BLEServer *pServer = BLEDevice::createServer();
  BLEService *pService = pServer->createService(SERVICE_UUID);

  BLECharacteristic *pCharactaristic = pService->createCharacteristic(
    CHARACTARISTIC_UUID,
    BLECharacteristic::PROPERTY_WRITE);

  pServer->setCallbacks(new MyServerCallback());

  pCharactaristic->setCallbacks(new MyCharacteristicCallback());
  pService->start();

  pServer->getAdvertising()->addServiceUUID(SERVICE_UUID);
  pServer->getAdvertising()->start();
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
