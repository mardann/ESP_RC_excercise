#include "DriveHAL.h"

DriveHAL::DriveHAL(uint8_t in1Pin, uint8_t in2Pin, uint8_t servoPin,
                   uint8_t motorChannelA, uint8_t motorChannelB,
                   uint8_t servoChannel, uint32_t motorFreq,
                   uint8_t pwmResolution, uint32_t servoFreq,
                   uint8_t servoResolution)
    : _in1Pin(in1Pin), _in2Pin(in2Pin), _servoPin(servoPin),
      _motorChannelA(motorChannelA), _motorChannelB(motorChannelB),
      _servoChannel(servoChannel), _motorFreq(motorFreq),
      _pwmResolution(pwmResolution), _servoFreq(servoFreq),
      _servoResolution(servoResolution) {}

void DriveHAL::begin() {
  pinMode(_in1Pin, OUTPUT);
  pinMode(_in2Pin, OUTPUT);
  digitalWrite(_in1Pin, LOW);
  digitalWrite(_in2Pin, LOW);

  ledcSetup(_motorChannelA, _motorFreq, _pwmResolution);
  ledcAttachPin(_in1Pin, _motorChannelA);

  ledcSetup(_motorChannelB, _motorFreq, _pwmResolution);
  ledcAttachPin(_in2Pin, _motorChannelB);

  pinMode(_servoPin, OUTPUT);
  ledcSetup(_servoChannel, _servoFreq, _servoResolution);
  ledcAttachPin(_servoPin, _servoChannel);

  centerSteering();
}

void DriveHAL::stop() {
  ledcWrite(_motorChannelA, 0);
  ledcWrite(_motorChannelB, 0);
}

void DriveHAL::setSpeed(int8_t speed) {
  int absSpeed = abs(speed);
  int pwmValue = map(absSpeed, 0, 100, 0, 180);

  if (speed > 5) {
    ledcWrite(_motorChannelA, pwmValue);
    ledcWrite(_motorChannelB, 0);
  } else if (speed < -5) {
    ledcWrite(_motorChannelA, 0);
    ledcWrite(_motorChannelB, pwmValue);
  } else {
    stop();
  }
}

void DriveHAL::setSteeringAngle(float angle) {
  ledcWrite(_servoChannel, mapServoAngle(angle));
}

void DriveHAL::centerSteering() {
  setSteeringAngle(90.0f);
}

int DriveHAL::mapServoAngle(float angle) const {
  if (angle < 0.0f) {
    angle = 0.0f;
  } else if (angle > 180.0f) {
    angle = 180.0f;
  }
  return map((int)angle, 0, 180, 102, 512);
}
