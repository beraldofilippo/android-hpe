# android-hpe
Android native application to perform head pose estimation using images coming from the front camera.

## Example of the final result
![Demo gif](https://j.gifs.com/mwX0Q3.gif)

## How to use
You need to compile your JNI libraries, they have to be copied to `[this_project_root]/dlib/src/main/jniLibs` (create the folder if it doesn't exist, careful about the upper case L).

Make sure to take a look at [android-hpe-library](https://github.com/beraldofilippo/android-hpe-library) before doing anything else in this project, it will help to understand how this app works.

## Basics
Under construction.

## Additional info
This algorithm can handle roughly up to the half-profile pose of the head, then it looses track.

I tried to expand the app's capacity to see full profile faces, with no luck. 
Basically I tried to learn a new classifier for the face detection and for the landmark detection, face detection worked but facial landmark detection didn't. I've decided to keep it as is was.

Please see [here](https://sourceforge.net/p/dclib/discussion/442518/thread/e80e526e/) for a collection of my rants about not getting a working algorithm, Davis has been very supportive though, I guess it's a bit of a pain to handle such volume of requests and questions.

In an effort to assess the precision of the estimation of the YAW (left/right displacement of the face from the front), I have got good precision (max. +/-6°) for the near-frontal views with a slow degrading towards the extreme poses (however no more than +/-15°). As for PITCH (up/down displacement of the face from the front) I dind't have enough time to evaluate (probably something similar).

For additional resources and to understand how this works, check out [dlib](http://dlib.net/), the core of the algorithms involved is from there.

## Credits
This app was developed as part of my MSc thesis in Computer Engineering at the University of Padua, Italy. Huge thanks to the Centro di Sonologia Computazionale (CSC) of the University of Padua [http://smc.dei.unipd.it/](http://smc.dei.unipd.it/).

This app, as well as its C++ counterpart, replicates the great job done in [dlib-android-app](https://github.com/tzutalin/dlib-android-app), please refer to this project for some additional info.

Credits also go to [gazr](https://github.com/severin-lemaignan/gazr), make sure to check it out as well.

Remember to show some love as well to Davis King at [dlib](https://github.com/davisking/dlib), whose work is great.

Plase leave a STAR to each of these projects, as well as this one.