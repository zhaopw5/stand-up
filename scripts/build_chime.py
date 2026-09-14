import math, wave, struct
rate=44100
duration=4.0
notes=[(0.0,523.25),(0.42,659.25),(0.84,783.99),(1.26,659.25),(1.68,587.33),(2.10,523.25)]
frames=[]
for i in range(int(rate*duration)):
 t=i/rate;v=0.0
 for start,f in notes:
  age=t-start
  if 0<=age<1.6:
   envelope=min(age/0.045,1)*math.exp(-3.2*age)*min((1.6-age)/0.2,1)
   v+=0.16*envelope*(math.sin(2*math.pi*f*age)+0.12*math.sin(2*math.pi*2*f*age))
 frames.append(struct.pack('<h',round(max(-1,min(1,v))*32767)))
with wave.open('assets/gentle-chime.wav','wb') as out:
 out.setnchannels(1);out.setsampwidth(2);out.setframerate(rate);out.writeframes(b''.join(frames))
print('Created gentle 4-second six-note chime')
