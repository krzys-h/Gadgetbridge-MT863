package nodomain.freeyourgadget.gadgetbridge.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.preference.ListPreference;

import nodomain.freeyourgadget.gadgetbridge.R;

public class XListPreference extends ListPreference {

    private final String CLASS_NAME = this.getClass().getSimpleName();
    private String dependentValue = "";

    public XListPreference(Context context) {
        this(context, null);
    }
    public XListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.XListPreference);
            dependentValue = a.getString(R.styleable.XListPreference_dependentValue);
            a.recycle();
        }
    }

    @Override
    public void setValue(String value) {
        String mOldValue = getValue();
        super.setValue(value);
        if (!value.equals(mOldValue)) {
            notifyDependencyChange(shouldDisableDependents());
        }
    }

    @Override
    public boolean shouldDisableDependents() {
        boolean shouldDisableDependents = super.shouldDisableDependents();
        String value = getValue();
        return shouldDisableDependents || value == null || !value.equals(dependentValue);
    }
}
