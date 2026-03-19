#include <BLEDevice.h>
#include <BLEServer.h>
#include <LiquidCrystal_I2C.h>
#include <ESP32Servo.h>

#define SERVICE_UUID "6a0ff9be-1bce-46e5-9013-3b9ec78a338e"
#define CHARACTARISTIC_UUID "0bed3ebb-e9b0-4b4e-95f4-44677fd04f24"

LiquidCrystal_I2C lcd(0x27, 16, 2);

int8_t x = 0;
int8_t y = 0;
bool dataStreamStarted = false;
unsigned long lastRefreshTime = 0;
const int refreshInterval = 200;


//servo control
const int SERVO_PIN = 13;
Servo steeringServo;

float targetAngle = 90.0;


//DC motor params:
const int motorIn1 = 25;
const int motorIn2 = 26;
const int motorEna = 27;

const int motorFreq = 5000;
const int motorChannel = 1; // timer 0 is allocated to steering
const int pwmResolution = 8;

int targetSpeed = 0;



class MyCallback : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharactaristic) {
    uint8_t *data = pCharactaristic->getData();
    if (pCharactaristic->getLength() >= 2) {
      if(!dataStreamStarted){
        dataStreamStarted = true;
      };
      x = (int8_t)data[0];
      y = (int8_t)data[1];

      targetAngle = map(x, -100, 100, 0, 180);
targetSpeed = y;      


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

  bluetoothInit();

  // Servo setup
  ESP32PWM::allocateTimer(0);
  steeringServo.setPeriodHertz(50);
  steeringServo.attach(SERVO_PIN, 500, 2400);
  steeringServo.write(90);
  
  // Motor setup
  pinMode(motorIn1, OUTPUT);
  pinMode(motorIn2, OUTPUT);
  ledcAttach(motorEna, motorFreq, pwmResolution);
}


void loop() {
  // put your main code here, to run repeatedly:
  printToScreen();

  if(dataStreamStarted){
    // float diff = targetAngle - smoothedAngle;
    // smoothedAngle += diff * smoothingFactor;
    // steeringServo.write((int)smoothedAngle);
    // Serial.print("Target: "); Serial.print(targetAngle); 
    steeringServo.write(targetAngle);

    motorControll(targetSpeed);
    
   

  }

  delay(10);
}

void motorControll(int8_t speed){

  int absSpeed = abs(speed);
  int pwmValue = map(absSpeed, 0, 100, 0, 255);

  if(speed > 5){
    digitalWrite(motorIn1, HIGH);
    digitalWrite(motorIn2, LOW);
    ledcWrite(motorEna, absSpeed);
  } else if(speed < 5){
    digitalWrite(motorIn1, LOW);
    digitalWrite(motorIn2, HIGH);
    ledcWrite(motorEna, absSpeed);
  } else {
    digitalWrite(motorIn1, LOW);
    digitalWrite(motorIn2, LOW);
    ledcWrite(motorEna, 0);
  }

}

void bluetoothInit() {
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
