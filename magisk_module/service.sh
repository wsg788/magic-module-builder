#!/system/bin/sh
# Magic Camera Spoofer Service
# This script reads the selected app and media from the Magic Cam app and logs them.

APP_FILE="/data/data/com.androidfakecam.magiccamapp/files/selected_app.txt"
MEDIA_DIR="/data/data/com.androidfakecam.magiccamapp/files/spoof_media"
LOG_FILE="/data/local/tmp/magic_cam_spoofer.log"

selected_app=""
if [ -f "$APP_FILE" ]; then
    selected_app=$(cat "$APP_FILE")
fi

# Find a current media file in spoof_media directory
media_file=""
if [ -d "$MEDIA_DIR" ]; then
    # Look for current_media.* first, fallback to first file
    if [ -f "$MEDIA_DIR/current_media.jpg" ]; then
        media_file="$MEDIA_DIR/current_media.jpg"
    elif [ -f "$MEDIA_DIR/current_media.mp4" ]; then
        media_file="$MEDIA_DIR/current_media.mp4"
    else
        media_file=$(ls "$MEDIA_DIR" 2>/dev/null | head -n1)
        [ -n "$media_file" ] && media_file="$MEDIA_DIR/$media_file"
    fi
fi

# Log the values
{
    echo "Selected App: $selected_app"
    echo "Media File: $media_file"
} > "$LOG_FILE"

# TODO: Implement camera spoofing logic using vcam or custom implementation
# For example, start the virtual camera service and feed frames from $media_file to the camera.

exit 0
