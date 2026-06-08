#ifndef DRIVE_HAL_H
#define DRIVE_HAL_H

#include <Arduino.h>

class DriveHAL {
public:
  DriveHAL(uint8_t in1Pin, uint8_t in2Pin, uint8_t servoPin,
           uint8_t motorChannelA = 2, uint8_t motorChannelB = 3,
           uint8_t servoChannel = 1, uint32_t motorFreq = 500,
           uint8_t pwmResolution = 8, uint32_t servoFreq = 50,
           uint8_t servoResolution = 12);

  void begin();
  void stop();
  void setSpeed(int8_t speed);
  void setSteeringAngle(float angle);
  void centerSteering();

private:
  uint8_t _in1Pin;
  uint8_t _in2Pin;
  uint8_t _servoPin;
  uint8_t _motorChannelA;
  uint8_t _motorChannelB;
  uint8_t _servoChannel;
  uint32_t _motorFreq;
  uint8_t _pwmResolution;
  uint32_t _servoFreq;
  uint8_t _servoResolution;

  int mapServoAngle(float angle) const;
};

#endif // DRIVE_HAL_H
