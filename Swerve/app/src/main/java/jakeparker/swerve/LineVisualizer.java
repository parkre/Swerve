package jakeparker.swerve;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * Created by jacobparker on 4/9/16.
 */
public class LineVisualizer extends View
{
    Paint paint = new Paint();

    private int lineLength = 20;

    public LineVisualizer(Context context)
    {
        super(context);
        paint.setColor(Color.BLACK);
    }

    @Override
    public void onDraw(Canvas canvas)
    {
        canvas.drawLine(0, 0, lineLength, lineLength, paint);
    }

}
