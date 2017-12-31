package tcking.github.com.giraffeplayer2;

import android.app.Activity;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.FloatMath;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.ScaleAnimation;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import com.github.tcking.giraffeplayer2.R;

import tcking.github.com.giraffeplayer2.trackselector.TrackSelectorFragment;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkTimedText;

/**
 * media controller for ListView or RecyclerView
 * Created by tcking on 2017
 */

public class DefaultMediaController extends BaseMediaController {

    private static final int STATUS_ERROR = -1;
    private static final int STATUS_IDLE = 0;
    private static final int STATUS_LOADING = 1;
    private static final int STATUS_PLAYING = 2;
    private static final int STATUS_PAUSE = 3;
    private static final int STATUS_COMPLETED = 4;
    private static final String TAG ="MediaController" ;

    protected long newPosition = -1;
    protected boolean isShowing;
    protected boolean isDragging;

    protected boolean instantSeeking;
    protected SeekBar seekBar;

    protected int volume = -1;
    protected final int maxVolume;


    protected float brightness;
    private int status = STATUS_IDLE;
    private int displayModel = GiraffePlayer.DISPLAY_NORMAL;

    private String generateTime(long time) {
        int totalSeconds = (int) (time / 1000);
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        return hours > 0 ? String.format("%02d:%02d:%02d", hours, minutes, seconds) : String.format("%02d:%02d", minutes, seconds);
    }

    protected final SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!fromUser)
                return;
            if (!videoView.isCurrentActivePlayer()) {
                return;
            }
            $.id(R.id.app_video_status).gone();// Hiding the status while moving image
            GiraffePlayer player = videoView.getPlayer();
            int newPosition = (int) ((player.getDuration() * progress * 1.0) / 1000);
            String time = generateTime(newPosition);
            if (instantSeeking) {
                player.seekTo(newPosition);

            }
            $.id(R.id.app_video_currentTime).text(time);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            isDragging = true;
            show(3600000);
            handler.removeMessages(MESSAGE_SHOW_PROGRESS);
            if (instantSeeking) {
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
            }
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (!videoView.isCurrentActivePlayer()) {
                return;
            }
            GiraffePlayer player = videoView.getPlayer();
            if (!instantSeeking) {
                player.seekTo((int) ((player.getDuration() * seekBar.getProgress() * 1.0) / 1000));
            }
            show(defaultTimeout);
            handler.removeMessages(MESSAGE_SHOW_PROGRESS);
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
            isDragging = false;
            handler.sendEmptyMessageDelayed(MESSAGE_SHOW_PROGRESS, 1000);
        }
    };

    protected void updatePausePlay() {
        if (videoView.isCurrentActivePlayer()) {
            boolean playing = videoView.getPlayer().isPlaying();
            if (playing) {
                $.id(R.id.app_video_play).image(R.drawable.ic_stop_white_24dp);
            } else {
                $.id(R.id.app_video_play).image(R.drawable.ic_play_arrow_white_24dp);
            }
        } else {
            $.id(R.id.app_video_play).image(R.drawable.ic_play_arrow_white_24dp);
        }
    }


    protected long setProgress() {
        if (isDragging) {
            return 0;
        }
        //check player is active
        boolean currentPlayer = videoView.isCurrentActivePlayer();
        if (!currentPlayer) {
            seekBar.setProgress(0);
            return 0;
        }

        //check player is ready
        GiraffePlayer player = videoView.getPlayer();
        int currentState = player.getCurrentState();
        if (currentState == GiraffePlayer.STATE_IDLE ||
                currentState == GiraffePlayer.STATE_PREPARING ||
                currentState == GiraffePlayer.STATE_ERROR) {
            return 0;
        }

        long position = player.getCurrentPosition();
        int duration = player.getDuration();

        if (seekBar != null) {
            if (duration > 0) {
                long pos = 1000L * position / duration;
                seekBar.setProgress((int) pos);
            }
            int percent = player.getBufferPercentage();
            seekBar.setSecondaryProgress(percent * 10);
        }

        $.id(R.id.app_video_currentTime).text(generateTime(position));
        if (duration == 0) {//live stream
            $.id(R.id.app_video_endTime).text(R.string.giraffe_player_live);
        } else {
            $.id(R.id.app_video_endTime).text(generateTime(duration));
        }
        return position;
    }


    protected void show(int timeout) {
        if (!isShowing) {
            if (videoView.getVideoInfo().isShowTopBar() || displayModel == GiraffePlayer.DISPLAY_FULL_WINDOW) {
                $.id(R.id.app_video_top_box).visible();
                $.id(R.id.app_video_title).text(videoView.getVideoInfo().getTitle());
            } else {
                $.id(R.id.app_video_top_box).gone();
            }
            showBottomControl(true);
            isShowing = true;
        }
        updatePausePlay();
        handler.sendEmptyMessage(MESSAGE_SHOW_PROGRESS);
        handler.removeMessages(MESSAGE_FADE_OUT);
        if (timeout != 0) {
            handler.sendMessageDelayed(handler.obtainMessage(MESSAGE_FADE_OUT), timeout);
        }

    }


    protected void showBottomControl(boolean show) {
        if (displayModel==GiraffePlayer.DISPLAY_FLOAT) {
            show = false;
        }
//        $.id(R.id.app_video_play).visibility(show ? View.VISIBLE : View.GONE);
//        $.id(R.id.app_video_currentTime).visibility(show ? View.VISIBLE : View.GONE);
//        $.id(R.id.app_video_endTime).visibility(show ? View.VISIBLE : View.GONE);
//        $.id(R.id.app_video_seekBar).visibility(show ? View.VISIBLE : View.GONE);
//        $.id(R.id.app_video_fullscreen).visibility(show ? View.VISIBLE : View.GONE);
        $.id(R.id.app_video_bottom_box).visibility(show ? View.VISIBLE : View.GONE);

    }

    protected void hide(boolean force) {
        if (force || isShowing) {
            handler.removeMessages(MESSAGE_SHOW_PROGRESS);
            showBottomControl(false);
            $.id(R.id.app_video_top_box).gone();
//            $.id(R.id.app_video_fullscreen).invisible();
            isShowing = false;
        }

    }

    ScalableTextureView scalableTextureView;
    public DefaultMediaController(Context context) {
        super(context);
        scalableTextureView=new ScalableTextureView(context);
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    @Override
    protected View makeControllerView() {
        return LayoutInflater.from(context).inflate(R.layout.giraffe_media_controller, videoView, false);
    }

    protected final View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            GiraffePlayer player = videoView.getPlayer();
            if (v.getId() == R.id.app_video_fullscreen) {
                player.toggleFullScreen();
            } else if (v.getId() == R.id.app_video_play) {
                if (player.isPlaying()) {
                    player.pause();
                } else {
                    player.start();
                }
            } else if (v.getId() == R.id.app_video_replay_icon) {
                player.seekTo(0);
                player.start();
//                videoView.seekTo(0);
//                videoView.start();
//                doPauseResume();
            } else if (v.getId() == R.id.app_video_finish) {
                if (!player.onBackPressed()) {
                    ((Activity) videoView.getContext()).finish();
                }
            }   else if (v.getId() == R.id.app_video_float_close) {
                player.stop();
                player.setDisplayModel(GiraffePlayer.DISPLAY_NORMAL);
            } else if (v.getId() == R.id.app_video_float_full) {
                player.setDisplayModel(GiraffePlayer.DISPLAY_FULL_WINDOW);
            }
            else if (v.getId() == R.id.app_video_clarity)
            {
                Activity activity = (Activity) videoView.getContext();
                if (activity instanceof AppCompatActivity) {
                    TrackSelectorFragment trackSelectorFragment = new TrackSelectorFragment();
                    Bundle bundle = new Bundle();
                    bundle.putString("fingerprint", videoView.getVideoInfo().getFingerprint());
                    trackSelectorFragment.setArguments(bundle);
                    FragmentManager supportFragmentManager = ((AppCompatActivity) activity).getSupportFragmentManager();
                    trackSelectorFragment.show(supportFragmentManager, "player_track");
                }

            }
        }
    };


    protected GestureDetector.OnGestureListener createGestureListener() {
        return new PlayerGestureListener();
    }



    @Override
    protected void initView(View view) {
        seekBar = $.id(R.id.app_video_seekBar).view();
        seekBar.setMax(1000);
        seekBar.setOnSeekBarChangeListener(seekListener);
        $.id(R.id.app_video_play).clicked(onClickListener);
        $.id(R.id.app_video_fullscreen).clicked(onClickListener);
        $.id(R.id.app_video_finish).clicked(onClickListener);
        $.id(R.id.app_video_replay_icon).clicked(onClickListener);
        $.id(R.id.app_video_clarity).clicked(onClickListener);
        $.id(R.id.app_video_float_close).clicked(onClickListener);
        $.id(R.id.app_video_float_full).clicked(onClickListener);


//
        final GestureDetector gestureDetector = new GestureDetector(context, createGestureListener());

        final ScaleGestureDetector scaleGestureDetector=new ScaleGestureDetector(context, new ScaleListener());
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if(scaleGestureDetector.onTouchEvent(event))
                {
                    //return true;
                }

                if (displayModel==GiraffePlayer.DISPLAY_FLOAT) {
                    return false;
                }

                if (gestureDetector.onTouchEvent(event)) {
                    return true;
                }

                // Handle gesture ends
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_OUTSIDE:
                        endGesture();
                        break;
                }
                return true;
            }
        });
    }

//    public class LiteGestureListener extends GestureDetector.SimpleOnGestureListener {
//        @Override
//        public boolean onSingleTapUp(MotionEvent e) {
//            boolean currentPlayer = videoView.isCurrentActivePlayer();
//            if (!currentPlayer) {
//                return true;
//            }
//            if (isShowing) {
//                hide(false);
//            } else {
//                show(defaultTimeout);
//            }
//            return true;
//        }
//    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MESSAGE_FADE_OUT:
                hide(false);
                break;
            case MESSAGE_HIDE_CENTER_BOX:
                $.id(R.id.app_video_volume_box).gone();
                $.id(R.id.app_video_brightness_box).gone();
                $.id(R.id.app_video_fastForward_box).gone();
                break;
            case MESSAGE_SEEK_NEW_POSITION:
                if (newPosition >= 0) {
                    videoView.getPlayer().seekTo((int) newPosition);
                    newPosition = -1;
                }
                break;
            case MESSAGE_SHOW_PROGRESS:
                setProgress();
                if (!isDragging && isShowing) {
                    msg = handler.obtainMessage(MESSAGE_SHOW_PROGRESS);
                    handler.sendMessageDelayed(msg, 300);
                    updatePausePlay();
                }
                break;
            case MESSAGE_RESTART_PLAY:
//                        play(url);
                break;
        }
        return true;
    }

    @Override
    public void onCompletion(GiraffePlayer giraffePlayer)
    {
        statusChange(STATUS_COMPLETED);
    }

    @Override
    public void onRelease(GiraffePlayer giraffePlayer) {
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onStart(GiraffePlayer giraffePlayer) {
        $.id(R.id.app_video_replay).gone();
        show(defaultTimeout);
    }


    protected void endGesture() {
        volume = -1;
        brightness = -1f;
        if (newPosition >= 0) {
            handler.removeMessages(MESSAGE_SEEK_NEW_POSITION);
            handler.sendEmptyMessage(MESSAGE_SEEK_NEW_POSITION);
        }
        handler.removeMessages(MESSAGE_HIDE_CENTER_BOX);
        handler.sendEmptyMessageDelayed(MESSAGE_HIDE_CENTER_BOX, 500);
    }
    private boolean firstTouch=false;
    private boolean volumeControl=false;
    private boolean toSeek=false;
    private boolean toZoom=false;
    public class PlayerGestureListener extends GestureDetector.SimpleOnGestureListener {



        /**
         * Double click
         */
        @Override
        public boolean onDoubleTap(MotionEvent e) {
//            Toast.makeText(context, "onDoubleTap", Toast.LENGTH_SHORT).show();
            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            firstTouch = true;
            return true;

        }
        /**
         *
         slide
         */
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if(toZoom)
            {
                toZoom=false;
                return false;
            }
            //1. if not the active player,ignore
            boolean currentPlayer = videoView.isCurrentActivePlayer();
            if (!currentPlayer) {
                return true;
            }

            float oldX = e1.getX(), oldY = e1.getY();
            float deltaY = oldY - e2.getY();
            float deltaX = oldX - e2.getX();
            if (firstTouch) {
                toSeek = Math.abs(distanceX) >= Math.abs(distanceY);
                volumeControl = oldX > videoView.getWidth() * 0.5f;
                firstTouch = false;

            }
            GiraffePlayer player = videoView.getPlayer();
            if (toSeek) {
                //$.id(R.id.app_video_center_box).visible();
                if (player.canSeekForward()) {
                    onProgressSlide(-deltaX / videoView.getWidth());
                }
            } else {
                //if player in list controllerView,ignore
                if (videoView.inListView()) {
                    return true;
                }
                float percent = deltaY / videoView.getHeight();
                if (volumeControl) {
                    //$.id(R.id.app_video_center_box).visible();
                    onVolumeSlide(percent);
                } else {
                   // $.id(R.id.app_video_center_box).visible();
                    onBrightnessSlide(percent);
                }
            }
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (isShowing) {
                hide(false);
            } else {
                show(defaultTimeout);
            }
            return true;
        }
    }


    private float scale = 1f;

    //private float mScale = 1f;
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {

            toZoom=true;
            $.id(R.id.app_video_zoom_box).visible();
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {


            toZoom=true;
            onZoom();
            //scalableTextureView.setId(R.id.player_display);
            scale *= detector.getScaleFactor();
            scale = Math.max(0.1f, Math.min(scale, 5.0f));

            GiraffePlayer player = videoView.getPlayer();
            scalableTextureView=player.getCurrentDisplay();
            scalableTextureView.setScaleY(scale);
            scalableTextureView.setScaleX(scale);
            return true;

            /*

            float scale = 1 - detector.getScaleFactor();

            float prevScale = mScale;
            mScale += scale;
            if (mScale < 0.1f) // Minimum scale condition:
                mScale = 0.1f;

            if (mScale > 10f) // Maximum scale condition:
                mScale = 10f;

            ScaleAnimation scaleAnimation = new ScaleAnimation(1f / prevScale,
                    1f / mScale, 1f / prevScale, 1f / mScale,
                    detector.getFocusX(), detector.getFocusY());
            scaleAnimation.setDuration(0);
            scaleAnimation.setFillAfter(true);


            videoView.setScaleX(mScale);
            videoView.setScaleY(mScale);

// your layout you want pinch zoom

           // videoView.getVideoInfo()

            //endGesture();


            scalableTextureView.setAnimation(scaleAnimation);
            //R.id.player_display;
            //videoView.setAnimation(scaleAnimation);
           return true;

           */
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            toZoom=false;
            $.id(R.id.app_video_zoom_box).gone();
        }
    }

    private void onZoom() {
        //firstTouch=false;
        volumeControl=false;
        toSeek=false;
        //$.id(R.id.app_video_center_box).gone();

        //$.id(R.id.app_video_volume_icon).gone();
       /* $.id(R.id.app_video_brightness_box).gone();
        $.id(R.id.app_video_volume_box).gone();
        $.id(R.id.app_video_volume_box).gone();
        $.id(R.id.app_video_fastForward_box).gone();
        $.id(R.id.app_video_brightness).gone();*/
    }
    /**
     *
     Swipe to change the sound size
     *
     * @param percent
     */
    private void onVolumeSlide(float percent) {
        if (volume == -1) {
            volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (volume < 0)
                volume = 0;
        }
        hide(true);

        int index = (int) (percent * maxVolume) + volume;
        if (index > maxVolume)
            index = maxVolume;
        else if (index < 0)
            index = 0;

        // 变更声音
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);

        // 变更进度条
        int i = (int) (index * 1.0 / maxVolume * 100);
        String s = i + "%";
        if (i == 0) {
            s = "off";
        }
        // 显示
        $.id(R.id.app_video_volume_icon).image(i == 0 ? R.drawable.ic_volume_off_white_36dp : R.drawable.ic_volume_up_white_36dp);
        $.id(R.id.app_video_brightness_box).gone();
        $.id(R.id.app_video_volume_box).visible();
        $.id(R.id.app_video_volume_box).visible();
        $.id(R.id.app_video_volume).text(s).visible();
    }

    private void onProgressSlide(float percent) {
        GiraffePlayer player = videoView.getPlayer();
        long position = player.getCurrentPosition();
        long duration = player.getDuration();
        long deltaMax = Math.min(100 * 1000, duration - position);
        long delta = (long) (deltaMax * percent);


        newPosition = delta + position;
        if (newPosition > duration) {
            newPosition = duration;
        } else if (newPosition <= 0) {
            newPosition = 0;
            delta = -position;
        }
        int showDelta = (int) delta / 1000;
        if (showDelta != 0) {
            $.id(R.id.app_video_fastForward_box).visible();
            String text = showDelta > 0 ? ("+" + showDelta) : "" + showDelta;
            $.id(R.id.app_video_fastForward).text(text + "s");
            $.id(R.id.app_video_fastForward_target).text(generateTime(newPosition) + "/");
            $.id(R.id.app_video_fastForward_all).text(generateTime(duration));
        }
//        handler.sendEmptyMessage(MESSAGE_SEEK_NEW_POSITION);
    }

    /**
     * 滑动改变亮度
     *
     * @param percent
     */
    private void onBrightnessSlide(float percent) {
        Window window = ((Activity) context).getWindow();
        if (brightness < 0) {
            brightness = window.getAttributes().screenBrightness;
            if (brightness <= 0.00f) {
                brightness = 0.50f;
            } else if (brightness < 0.01f) {
                brightness = 0.01f;
            }
        }
        Log.d(this.getClass().getSimpleName(), "brightness:" + brightness + ",percent:" + percent);
        $.id(R.id.app_video_brightness_box).visible();
        WindowManager.LayoutParams lpa = window.getAttributes();
        lpa.screenBrightness = brightness + percent;
        if (lpa.screenBrightness > 1.0f) {
            lpa.screenBrightness = 1.0f;
        } else if (lpa.screenBrightness < 0.01f) {
            lpa.screenBrightness = 0.01f;
        }
        $.id(R.id.app_video_brightness).text(((int) (lpa.screenBrightness * 100)) + "%");
        window.setAttributes(lpa);

    }

    @Override
    public boolean onInfo(GiraffePlayer giraffePlayer, int what, int extra) {
        switch (what) {
            case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                statusChange(STATUS_LOADING);
                break;
            case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                statusChange(STATUS_PLAYING);
                break;
            case IMediaPlayer.MEDIA_INFO_NETWORK_BANDWIDTH:
                //显示 下载速度
//                        Toaster.show("download rate:" + extra);
                break;
            case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                statusChange(STATUS_PLAYING);
                break;

            default:
        }

        return true;
    }

    private void statusChange(int status) {
        this.status = status;
        switch (status) {
            case STATUS_LOADING:
                $.id(R.id.app_video_loading).visible();
                $.id(R.id.app_video_status).gone();
                break;
            case STATUS_PLAYING:
                $.id(R.id.app_video_loading).gone();
                $.id(R.id.app_video_status).gone();
                break;
            case STATUS_COMPLETED:
                handler.removeMessages(MESSAGE_SHOW_PROGRESS);
                showBottomControl(false);
                $.id(R.id.app_video_replay).visible();
                $.id(R.id.app_video_loading).gone();
                $.id(R.id.app_video_status).gone();
                break;
            case STATUS_ERROR:
                $.id(R.id.app_video_status).visible().text("something error");
                handler.removeMessages(MESSAGE_SHOW_PROGRESS);
                $.id(R.id.app_video_loading).gone();
                break;
            default:
        }
    }

    @Override
    public boolean onError(GiraffePlayer giraffePlayer, int what, int extra) {
        statusChange(STATUS_ERROR);
        return true;
    }

    @Override
    public void onPrepared(GiraffePlayer giraffePlayer) {
        boolean live = giraffePlayer.getDuration() == 0;
        $.id(R.id.app_video_seekBar).enabled(!live);
        if (giraffePlayer.getTrackInfo().length > 0) {
            $.id(R.id.app_video_clarity).visible();
        } else {
            $.id(R.id.app_video_clarity).gone();
        }
    }

    @Override
    public void onPreparing(GiraffePlayer giraffePlayer) {
        statusChange(STATUS_LOADING);
    }

    @Override
    public void onDisplayModelChange(int oldModel, int newModel) {
        this.displayModel = newModel;
        if (displayModel == GiraffePlayer.DISPLAY_FLOAT) {
            $.id(R.id.app_video_float_close).visible();
            $.id(R.id.app_video_float_full).visible();
            $.id(R.id.app_video_bottom_box).gone();

        } else {
            $.id(R.id.app_video_float_close).gone();
            $.id(R.id.app_video_float_full).gone();
            $.id(R.id.app_video_bottom_box).visible();

        }
//        ((ViewGroup) controllerView.getParent()).removeView(controllerView);
//        if (newModel == GiraffePlayer.DISPLAY_FULL_WINDOW) {
//            ViewGroup top = (ViewGroup) ((Activity) videoView.getContext()).findViewById(android.R.id.content);
//            top.addView(controllerView);
//        } else if (newModel == GiraffePlayer.DISPLAY_NORMAL) {
//            videoView.addView(controllerView);
//        }
    }

    @Override
    public void onTargetStateChange(int oldState, int newState) {
        if (newState != GiraffePlayer.STATE_IDLE) {
            $.id(R.id.app_video_cover).gone();
        }
    }

    @Override
    public void onTimedText(GiraffePlayer giraffePlayer, IjkTimedText text) {
        if (text == null) {
            $.id(R.id.app_video_subtitle).gone();
        } else {
            $.id(R.id.app_video_subtitle).visible().text(text.getText());
        }
    }
}
