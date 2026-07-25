# Plan from human

- Make a plan and write it down into a file.

- Ask the user for information not provided that may be needed to make the app.

- Make a kotlin app for android.

- Where possible use the standard library.

- Where possible use the device provided APIs, input, file picker, camera, etc.

- The target devide is a tablet.

The app is about an app to take pictures in parties.

Use the front and back cameras.

The main screen when opening the app will be referred as "main".

By taping into the upper right corner of main it opens the config screen.

Main may display any of the following:

- nothing

- video, do not know what the format should be for an android app,
if .mp4 or other, choose,

- GIF

- image, a single imagen set in config with file picker, choose some format for it.

On top of this show a button to change between front and back cameras, the default is
front, set in config, this button do not change the default, only the one to be used
in this session of the app, meaning if you close the app and open again, you use the default,
make the display of this button optional from config.

When tapping once in any part of main the screen change to a preview of the selected
camera(default set in config) if the camera selected changed while the app is open
continue to that selection(but do not save this as the new default)

At the same time allow to set an image with transparency on top of the preview, set
in the config.

Display a downward counter that began when the preview is show, the counter is set in config

Once the counter reach zero, take a picture.

Save the picture to the path selected in config, with the name being the timestamp,
choose the appropriate format.

Keep showing the picture just taken.

Display a button to take another picture, which will take us to main.

Another button to show the galery, use some in app gallery not the default from the
device.

Create an architecture to allow to add more buttons and make all of them optional.

All user facing text has to be in spanish.

The idea is for the user(our clients) to always be inside the app, so make this app
fullscreen.

Send a signal to the device to not sleep or automatically block the device.

Make active use of git to keep good history of your changes.

The gallery to be show towards the user will be from a single folder pre-selected in config, all image taken will be saved there.

The config access for now is open to anyone, later on it will be password locked,
so take that in consideration when making it, but for now do not implement any password system.

All button are floating over whatever is being show.

All content show is pre loaded by the operator in the config.

Choose the most compatible android version, if at any point some api or functionality is lacking, let me know before you try to upgrade.
