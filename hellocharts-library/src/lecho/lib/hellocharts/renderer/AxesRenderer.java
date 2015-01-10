package lecho.lib.hellocharts.renderer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextUtils;

import lecho.lib.hellocharts.computator.ChartComputator;
import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.AxisValue;
import lecho.lib.hellocharts.model.Viewport;
import lecho.lib.hellocharts.util.AxisAutoValues;
import lecho.lib.hellocharts.util.ChartUtils;
import lecho.lib.hellocharts.util.FloatUtils;
import lecho.lib.hellocharts.view.Chart;

/**
 * Default axes renderer. Can draw maximum four axes - two horizontal(top/bottom) and two vertical(left/right).
 */
public class AxesRenderer {
	private static final int DEFAULT_AXIS_MARGIN_DP = 2;

	/**
	 * Axis positions indexes, used for indexing tabs that holds axes parameters, see below.
	 */
	private static final int TOP = 0;
	private static final int LEFT = 1;
	private static final int RIGHT = 2;
	private static final int BOTTOM = 3;

	/**
	 * Used to measure label width. If label has mas 5 characters only 5 first characters of this array are used to
	 * measure text width.
	 */
	private static final char[] labelWidthChars = new char[]{'0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0',
			'0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0'};

	private Chart chart;
	private ChartComputator computator;
	private int axisMargin;
	private float density;
	private float scaledDensity;
	private Paint[] textPaintTab = new Paint[]{new Paint(), new Paint(), new Paint(), new Paint()};
	private Paint linePaint;

	/**
	 * Holds formated axis value label.
	 */
	private char[] labelBuffer = new char[32];

	/**
	 * Holds number of values that should be drown for each axis.
	 */
	private int[] valuesToDrawNumTab = new int[4];

	/**
	 * Holds raw values to draw for each axis.
	 */
	private float[][] rawValuesTab = new float[4][0];

	/**
	 * Holds auto-generated values that should be drawn, i.e if axis is inside not all auto-generated values should be
	 * drawn to avoid overdrawing. Used only for auto axes.
	 */
	private float[][] autoValuesToDrawTab = new float[4][0];

	/**
	 * Holds custom values that should be drawn, used only for custom axes.
	 */
	private AxisValue[][] valuesToDrawTab = new AxisValue[4][0];

	/**
	 * Buffers for axes lines coordinates(to draw grid in the background).
	 */
	private float[][] linesDrawBufferTab = new float[4][0];

	/**
	 * Buffers for auto-generated values for each axis, used only if there are auto axes.
	 */
	private AxisAutoValues[] autoValuesBufferTab = new AxisAutoValues[]{new AxisAutoValues(),
			new AxisAutoValues(), new AxisAutoValues(), new AxisAutoValues()};

	private float[] nameBaselineTab = new float[4];
	private float[] labelBaselineTab = new float[4];
	private float[] separationLineTab = new float[4];
	private int[] labelWidthTab = new int[4];
	private int[] labelTextAscentTab = new int[4];
	private int[] labelTextDescentTab = new int[4];
	private int[] labelDimensionForMarginsTab = new int[4];
	private int[] labelDimensionForStepsTab = new int[4];
	private int[] tiltedLabelXTranslation = new int[4];
	private int[] tiltedLabelYTranslation = new int[4];
	private FontMetricsInt[] fontMetricsTab = new FontMetricsInt[]{new FontMetricsInt(), new FontMetricsInt(),
			new FontMetricsInt(), new FontMetricsInt()};

	public AxesRenderer(Context context, Chart chart) {
		this.chart = chart;
		computator = chart.getChartComputator();
		density = context.getResources().getDisplayMetrics().density;
		scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
		axisMargin = ChartUtils.dp2px(density, DEFAULT_AXIS_MARGIN_DP);
		linePaint = new Paint();
		linePaint.setAntiAlias(true);
		linePaint.setStyle(Paint.Style.STROKE);
		linePaint.setStrokeWidth(1);
		for (Paint paint : textPaintTab) {
			paint.setAntiAlias(true);
		}
	}

	public void onChartDataOrSizeChanged() {
		initAxis(chart.getChartData().getAxisXTop(), TOP);
		initAxis(chart.getChartData().getAxisXBottom(), BOTTOM);
		initAxis(chart.getChartData().getAxisYLeft(), LEFT);
		initAxis(chart.getChartData().getAxisYRight(), RIGHT);
	}

	/**
	 * Initialize attributes and measurement for axes(left, right, top, bottom);
	 */
	private void initAxis(Axis axis, int position) {
		if (null == axis) {
			return;
		}
		initAxisAttributes(axis, position);
		initAxisMargin(axis, position);
		initAxisMeasurements(axis, position);
	}

	private void initAxisAttributes(Axis axis, int position) {
		Typeface typeface = axis.getTypeface();
		if (null != typeface) {
			textPaintTab[position].setTypeface(typeface);
		}

		textPaintTab[position].setColor(axis.getTextColor());
		textPaintTab[position].setTextSize(ChartUtils.sp2px(scaledDensity, axis.getTextSize()));
		textPaintTab[position].getFontMetricsInt(fontMetricsTab[position]);

		labelTextAscentTab[position] = Math.abs(fontMetricsTab[position].ascent);
		labelTextDescentTab[position] = Math.abs(fontMetricsTab[position].descent);
		labelWidthTab[position] = (int) textPaintTab[position].measureText(labelWidthChars, 0,
				axis.getMaxLabelChars());

		if (axis.hasTiltedLabels()) {
			initAxisDimensionForTiltedLabels(position);
			intiTiltedLabelsTranslation(axis, position);
		} else {
			initAxisDimension(position);
		}
	}

	private void initAxisDimensionForTiltedLabels(int position) {
		int pythagoreanFromLabelWidth = (int) Math.sqrt(Math.pow(labelWidthTab[position], 2) / 2);
		int pythagoreanFromAscent = (int) Math.sqrt(Math.pow(labelTextAscentTab[position], 2) / 2);
		labelDimensionForMarginsTab[position] = pythagoreanFromAscent + pythagoreanFromLabelWidth;
		labelDimensionForStepsTab[position] = labelDimensionForMarginsTab[position];
	}

	private void initAxisDimension(int position) {
		if (LEFT == position || RIGHT == position) {
			labelDimensionForMarginsTab[position] = labelWidthTab[position];
			labelDimensionForStepsTab[position] = labelTextAscentTab[position];
		} else if (TOP == position || BOTTOM == position) {
			labelDimensionForMarginsTab[position] = labelTextAscentTab[position] +
					labelTextDescentTab[position];
			labelDimensionForStepsTab[position] = labelWidthTab[position];
		}
	}

	private void intiTiltedLabelsTranslation(Axis axis, int position) {
		int pythagoreanFromLabelWidth = (int) Math.sqrt(Math.pow(labelWidthTab[position], 2) / 2);
		int pythagoreanFromAscent = (int) Math.sqrt(Math.pow(labelTextAscentTab[position], 2) / 2);
		int dx = 0;
		int dy = 0;
		if (axis.isInside()) {
			if (LEFT == position) {
				dx = pythagoreanFromAscent;
			} else if (RIGHT == position) {
				dy = -pythagoreanFromLabelWidth / 2;
			} else if (TOP == position) {
				dy = (pythagoreanFromAscent + pythagoreanFromLabelWidth / 2) - labelTextAscentTab[position];
			} else if (BOTTOM == position) {
				dy = -pythagoreanFromLabelWidth / 2;
			}
		} else {
			if (LEFT == position) {
				dy = -pythagoreanFromLabelWidth / 2;
			} else if (RIGHT == position) {
				dx = pythagoreanFromAscent;
			} else if (TOP == position) {
				dy = -pythagoreanFromLabelWidth / 2;
			} else if (BOTTOM == position) {
				dy = (pythagoreanFromAscent + pythagoreanFromLabelWidth / 2) - labelTextAscentTab[position];
			}
		}
		tiltedLabelXTranslation[position] = dx;
		tiltedLabelYTranslation[position] = dy;
	}

	private void initAxisMargin(Axis axis, int position) {
		int margin = 0;
		if (!axis.isInside() && (axis.isAutoGenerated() || !axis.getValues().isEmpty())) {
			margin += axisMargin + labelDimensionForMarginsTab[position];
		}
		margin += getAxisNameMargin(axis, position);
		insetContentRectWithAxesMargins(margin, position);
	}

	private int getAxisNameMargin(Axis axis, int position) {
		int margin = 0;
		if (!TextUtils.isEmpty(axis.getName())) {
			margin += labelTextAscentTab[position];
			margin += labelTextDescentTab[position];
			margin += axisMargin;
		}
		return margin;
	}

	private void insetContentRectWithAxesMargins(int axisMargin, int position) {
		switch (position) {
			case LEFT:
				chart.getChartComputator().insetContentRect(axisMargin, 0, 0, 0);
				break;
			case TOP:
				chart.getChartComputator().insetContentRect(0, axisMargin, 0, 0);
				break;
			case RIGHT:
				chart.getChartComputator().insetContentRect(0, 0, axisMargin, 0);
				break;
			case BOTTOM:
				chart.getChartComputator().insetContentRect(0, 0, 0, axisMargin);
				break;
		}
	}

	private void initAxisMeasurements(Axis axis, int position) {
		if (LEFT == position) {
			if (axis.isInside()) {
				labelBaselineTab[position] = computator.getContentRectMinusAxesMargins().left + axisMargin;
				nameBaselineTab[position] = computator.getContentRectMinusAxesMargins().left - axisMargin
						- labelTextDescentTab[position];
			} else {
				labelBaselineTab[position] = computator.getContentRectMinusAxesMargins().left - axisMargin;
				nameBaselineTab[position] = labelBaselineTab[position] - axisMargin
						- labelTextDescentTab[position] - labelDimensionForMarginsTab[position];
			}
			separationLineTab[position] = computator.getContentRectMinusAllMargins().left;
		} else if (RIGHT == position) {
			if (axis.isInside()) {
				labelBaselineTab[position] = computator.getContentRectMinusAxesMargins().right - axisMargin;
				nameBaselineTab[position] = computator.getContentRectMinusAxesMargins().right + axisMargin
						+ labelTextAscentTab[position];
			} else {
				labelBaselineTab[position] = computator.getContentRectMinusAxesMargins().right + axisMargin;
				nameBaselineTab[position] = labelBaselineTab[position] + axisMargin
						+ labelTextAscentTab[position] + labelDimensionForMarginsTab[position];
			}
			separationLineTab[position] = computator.getContentRectMinusAllMargins().right;
		} else if (BOTTOM == position) {
			if (axis.isInside()) {
				labelBaselineTab[position] = computator.getContentRectMinusAxesMargins().bottom - axisMargin
						- labelTextDescentTab[position];
				nameBaselineTab[position] = computator.getContentRectMinusAxesMargins().bottom + axisMargin
						+ labelTextAscentTab[position];
			} else {
				labelBaselineTab[position] = computator.getContentRectMinusAxesMargins().bottom + axisMargin
						+ labelTextAscentTab[position];
				nameBaselineTab[position] = labelBaselineTab[position] + axisMargin
						+ labelTextDescentTab[position] + labelDimensionForMarginsTab[position];
			}
			separationLineTab[position] = computator.getContentRectMinusAllMargins().bottom;
		} else if (TOP == position) {
			if (axis.isInside()) {
				labelBaselineTab[position] = computator.getContentRectMinusAxesMargins().top + axisMargin
						+ labelTextAscentTab[position];
				nameBaselineTab[position] = computator.getContentRectMinusAxesMargins().top - axisMargin
						- labelTextDescentTab[position];
			} else {
				labelBaselineTab[position] = computator.getContentRectMinusAxesMargins().top - axisMargin
						- labelTextDescentTab[position];
				nameBaselineTab[position] = labelBaselineTab[position] - axisMargin
						- labelTextDescentTab[position] - labelDimensionForMarginsTab[position];
			}
			separationLineTab[position] = computator.getContentRectMinusAllMargins().top;
		} else {
			throw new IllegalArgumentException("Invalid axis position: " + position);
		}
	}

	/**
	 * Prepare axes coordinates and draw axes lines(if enabled) in the background.
	 *
	 * @param canvas
	 */
	public void drawInBackground(Canvas canvas) {
		Axis axis = chart.getChartData().getAxisYLeft();
		if (null != axis) {
			prepareAxisToDraw(axis, LEFT);
			drawAxisVerticalLines(canvas, axis, LEFT);
		}

		axis = chart.getChartData().getAxisYRight();
		if (null != axis) {
			prepareAxisToDraw(axis, RIGHT);
			drawAxisVerticalLines(canvas, axis, RIGHT);
		}

		axis = chart.getChartData().getAxisXBottom();
		if (null != axis) {
			prepareAxisToDraw(axis, BOTTOM);
			drawAxisHorizontalLines(canvas, axis, BOTTOM);
		}

		axis = chart.getChartData().getAxisXTop();
		if (null != axis) {
			prepareAxisToDraw(axis, TOP);
			drawAxisHorizontalLines(canvas, axis, TOP);
		}
	}

	private void prepareAxisToDraw(Axis axis, int position) {
		if (TOP == position || BOTTOM == position) {
			textPaintTab[position].setTextAlign(Align.CENTER);
			if (axis.isAutoGenerated()) {
				prepareAxisHorizontalAuto(axis, position);
			} else {
				prepareAxisHorizontalCustom(axis, position);
			}
		} else if (LEFT == position || RIGHT == position) {
			if (LEFT == position) {
				if (axis.isInside()) {
					textPaintTab[position].setTextAlign(Align.LEFT);
				} else {
					textPaintTab[position].setTextAlign(Align.RIGHT);
				}
			} else if (RIGHT == position) {
				if (axis.isInside()) {
					textPaintTab[position].setTextAlign(Align.RIGHT);
				} else {
					textPaintTab[position].setTextAlign(Align.LEFT);
				}
			}
			if (axis.isAutoGenerated()) {
				prepareAxisVerticalAuto(axis, position);
			} else {
				prepareAxisVerticalCustom(axis, position);
			}
		} else {
			throw new IllegalArgumentException("Invalid position for horizontal axis: " + position);
		}

	}

	/**
	 * Draw axes labels and names in the foreground.
	 *
	 * @param canvas
	 */
	public void drawInForeground(Canvas canvas) {
		Axis axis = chart.getChartData().getAxisYLeft();
		if (null != axis) {
			drawAxisVerticalLabels(canvas, axis, LEFT);
		}

		axis = chart.getChartData().getAxisYRight();
		if (null != axis) {
			drawAxisVerticalLabels(canvas, axis, RIGHT);
		}

		axis = chart.getChartData().getAxisXBottom();
		if (null != axis) {
			drawAxisHorizontalLabels(canvas, axis, BOTTOM);
		}

		axis = chart.getChartData().getAxisXTop();
		if (null != axis) {
			drawAxisHorizontalLabels(canvas, axis, TOP);
		}
	}

	// ********** HORIZONTAL X AXES ****************

	private void prepareAxisHorizontalCustom(Axis axis, int position) {
		final Viewport maxViewport = computator.getMaximumViewport();
		final Viewport visibleViewport = computator.getVisibleViewport();
		final Rect contentRect = computator.getContentRectMinusAllMargins();
		float scale = maxViewport.width() / visibleViewport.width();
		int module = (int) Math.ceil((axis.getValues().size() * labelDimensionForStepsTab[position])
				/ (contentRect.width() * scale));
		if (module < 1) {
			module = 1;
		}
		if (axis.hasLines() && linesDrawBufferTab[position].length < axis.getValues().size() * 4) {
			linesDrawBufferTab[position] = new float[axis.getValues().size() * 4];
		}
		if (rawValuesTab[position].length < axis.getValues().size()) {
			rawValuesTab[position] = new float[axis.getValues().size()];
			valuesToDrawTab[position] = new AxisValue[axis.getValues().size()];
		}

		int valueIndex = 0;
		int valueToDrawIndex = 0;
		for (AxisValue axisValue : axis.getValues()) {
			// Draw axis values that are within visible viewport.
			final float value = axisValue.getValue();
			if (value >= visibleViewport.left && value <= visibleViewport.right) {
				// Draw axis values that have 0 module value, this will hide some labels if there is no place for them.
				if (0 == valueIndex % module) {
					final float rawX = computator.computeRawX(axisValue.getValue());
					if (checkRawX(contentRect, rawX, axis.isInside(), position)) {
						rawValuesTab[position][valueToDrawIndex] = rawX;
						valuesToDrawTab[position][valueToDrawIndex] = axisValue;
						++valueToDrawIndex;
					}
				}
				// If within viewport - increment valueIndex;
				++valueIndex;
			}
		}
		valuesToDrawNumTab[position] = valueToDrawIndex;
	}

	private void prepareAxisHorizontalAuto(Axis axis, int position) {
		final Viewport visibleViewport = computator.getVisibleViewport();
		final Rect contentRect = computator.getContentRectMinusAllMargins();
		FloatUtils.computeAxisAutoValues(visibleViewport.left, visibleViewport.right, contentRect.width()
				/ labelDimensionForStepsTab[position] / 2, autoValuesBufferTab[position]);
		if (axis.hasLines()
				&& linesDrawBufferTab[position].length < autoValuesBufferTab[position].valuesNumber * 4) {
			linesDrawBufferTab[position] = new float[autoValuesBufferTab[position].valuesNumber * 4];
		}
		if (rawValuesTab[position].length < autoValuesBufferTab[position].valuesNumber) {
			rawValuesTab[position] = new float[autoValuesBufferTab[position].valuesNumber];
			autoValuesToDrawTab[position] = new float[autoValuesBufferTab[position].valuesNumber];
		}

		int valueToDrawIndex = 0;
		for (int i = 0; i < autoValuesBufferTab[position].valuesNumber; ++i) {
			final float rawX = computator.computeRawX(autoValuesBufferTab[position].values[i]);
			if (checkRawX(contentRect, rawX, axis.isInside(), position)) {
				rawValuesTab[position][valueToDrawIndex] = rawX;
				autoValuesToDrawTab[position][valueToDrawIndex] = autoValuesBufferTab[position].values[i];
				++valueToDrawIndex;
			}
		}
		valuesToDrawNumTab[position] = valueToDrawIndex;
	}

	private void drawAxisHorizontalLines(Canvas canvas, Axis axis, int position) {
		final Rect contentRectMargins = chart.getChartComputator().getContentRectMinusAxesMargins();
		// Draw separation line with the same color as axis text.
		if (axis.hasSeparationLine()) {
			canvas.drawLine(contentRectMargins.left, separationLineTab[position], contentRectMargins.right,
					separationLineTab[position], textPaintTab[position]);
		}
		if (!axis.hasLines()) {
			return;
		}

		int valueToDrawIndex = 0;
		for (; valueToDrawIndex < valuesToDrawNumTab[position]; ++valueToDrawIndex) {
			linesDrawBufferTab[position][valueToDrawIndex * 4 + 0] = rawValuesTab[position][valueToDrawIndex];
			linesDrawBufferTab[position][valueToDrawIndex * 4 + 1] = contentRectMargins.top;
			linesDrawBufferTab[position][valueToDrawIndex * 4 + 2] = rawValuesTab[position][valueToDrawIndex];
			linesDrawBufferTab[position][valueToDrawIndex * 4 + 3] = contentRectMargins.bottom;
		}
		linePaint.setColor(axis.getLineColor());
		canvas.drawLines(linesDrawBufferTab[position], 0, valueToDrawIndex * 4, linePaint);
	}

	private void drawAxisHorizontalLabels(Canvas canvas, Axis axis, int position) {
		for (int valueToDrawIndex = 0; valueToDrawIndex < valuesToDrawNumTab[position]; ++valueToDrawIndex) {
			int charsNumber = 0;
			if (axis.isAutoGenerated()) {
				final float value = autoValuesToDrawTab[position][valueToDrawIndex];
				charsNumber = axis.getFormatter().formatValueForAutoGeneratedAxis(labelBuffer, value,
						autoValuesBufferTab[position].decimals);
			} else {
				AxisValue axisValue = valuesToDrawTab[position][valueToDrawIndex];
				charsNumber = axis.getFormatter().formatValueForManualAxis(labelBuffer, axisValue);
			}

			if(axis.hasTiltedLabels()) {
				canvas.save();
				canvas.translate(tiltedLabelXTranslation[position], tiltedLabelYTranslation[position]);
				canvas.rotate(-45, rawValuesTab[position][valueToDrawIndex], labelBaselineTab[position]);
				canvas.drawText(labelBuffer, labelBuffer.length - charsNumber, charsNumber,
						rawValuesTab[position][valueToDrawIndex], labelBaselineTab[position],
						textPaintTab[position]);
				canvas.restore();
			}else{
				canvas.drawText(labelBuffer, labelBuffer.length - charsNumber, charsNumber,
						rawValuesTab[position][valueToDrawIndex], labelBaselineTab[position],
						textPaintTab[position]);
			}
		}

		// Drawing axis name
		final Rect contentRectMargins = chart.getChartComputator().getContentRectMinusAxesMargins();
		if (!TextUtils.isEmpty(axis.getName())) {
			textPaintTab[position].setTextAlign(Align.CENTER);
			canvas.drawText(axis.getName(), contentRectMargins.centerX(), nameBaselineTab[position],
					textPaintTab[position]);
		}
	}

	/**
	 * For axis inside chart area this method checks if there is place to draw axis label. If yes returns true,
	 * otherwise false.
	 */
	private boolean checkRawX(Rect rect, float rawX, boolean axisInside, int position) {
		if (axisInside) {
			float margin = labelWidthTab[position] / 2;
			if (rawX >= rect.left + margin && rawX <= rect.right - margin) {
				return true;
			} else {
				return false;
			}
		}
		return true;
	}

	// ********** VERTICAL Y AXES ****************

	private void prepareAxisVerticalCustom(Axis axis, int position) {
		final Viewport maxViewport = computator.getMaximumViewport();
		final Viewport visibleViewport = computator.getVisibleViewport();
		final Rect contentRect = computator.getContentRectMinusAllMargins();
		float scale = maxViewport.height() / visibleViewport.height();
		int module = (int) Math.ceil((axis.getValues().size() * labelDimensionForStepsTab[position] * 2)
				/ (contentRect.height() * scale));
		if (module < 1) {
			module = 1;
		}
		if (axis.hasLines() && linesDrawBufferTab[position].length < axis.getValues().size() * 4) {
			linesDrawBufferTab[position] = new float[axis.getValues().size() * 4];
		}
		if (rawValuesTab[position].length < axis.getValues().size()) {
			rawValuesTab[position] = new float[axis.getValues().size()];
			valuesToDrawTab[position] = new AxisValue[axis.getValues().size()];
		}

		int valueIndex = 0;
		int valueToDrawIndex = 0;
		for (AxisValue axisValue : axis.getValues()) {
			// Draw axis values that area within visible viewport.
			final float value = axisValue.getValue();
			if (value >= visibleViewport.bottom && value <= visibleViewport.top) {
				// Draw axis values that have 0 module value, this will hide some labels if there is no place for them.
				if (0 == valueIndex % module) {
					final float rawY = computator.computeRawY(value);
					if (checkRawY(contentRect, rawY, axis.isInside(), position)) {
						rawValuesTab[position][valueToDrawIndex] = rawY;
						valuesToDrawTab[position][valueToDrawIndex] = axisValue;
						++valueToDrawIndex;
					}
				}
				// If within viewport - increment valueIndex;
				++valueIndex;
			}
		}
		valuesToDrawNumTab[position] = valueToDrawIndex;
	}

	private void prepareAxisVerticalAuto(Axis axis, int position) {
		final Viewport visibleViewport = computator.getVisibleViewport();
		final Rect contentRect = computator.getContentRectMinusAllMargins();
		FloatUtils.computeAxisAutoValues(visibleViewport.bottom, visibleViewport.top, contentRect.height()
				/ labelDimensionForStepsTab[position] / 2, autoValuesBufferTab[position]);
		if (axis.hasLines()
				&& linesDrawBufferTab[position].length < autoValuesBufferTab[position].valuesNumber * 4) {
			linesDrawBufferTab[position] = new float[autoValuesBufferTab[position].valuesNumber * 4];
		}
		if (rawValuesTab[position].length < autoValuesBufferTab[position].valuesNumber) {
			rawValuesTab[position] = new float[autoValuesBufferTab[position].valuesNumber];
			autoValuesToDrawTab[position] = new float[autoValuesBufferTab[position].valuesNumber];
		}

		int stopsToDrawIndex = 0;
		for (int i = 0; i < autoValuesBufferTab[position].valuesNumber; i++) {
			final float rawY = computator.computeRawY(autoValuesBufferTab[position].values[i]);
			if (checkRawY(contentRect, rawY, axis.isInside(), position)) {
				rawValuesTab[position][stopsToDrawIndex] = rawY;
				autoValuesToDrawTab[position][stopsToDrawIndex] = autoValuesBufferTab[position].values[i];
				++stopsToDrawIndex;
			}
		}
		valuesToDrawNumTab[position] = stopsToDrawIndex;
	}

	private void drawAxisVerticalLines(Canvas canvas, Axis axis, int position) {
		final Rect contentRectMargins = chart.getChartComputator().getContentRectMinusAxesMargins();
		// Draw separation line with the same color as axis text.
		if (axis.hasSeparationLine()) {
			canvas.drawLine(separationLineTab[position], contentRectMargins.bottom,
					separationLineTab[position], contentRectMargins.top, textPaintTab[position]);
		}
		if (!axis.hasLines()) {
			return;
		}

		int stopsToDrawIndex = 0;
		for (; stopsToDrawIndex < valuesToDrawNumTab[position]; ++stopsToDrawIndex) {
			linesDrawBufferTab[position][stopsToDrawIndex * 4 + 0] = contentRectMargins.left;
			linesDrawBufferTab[position][stopsToDrawIndex * 4 + 1] = rawValuesTab[position][stopsToDrawIndex];
			linesDrawBufferTab[position][stopsToDrawIndex * 4 + 2] = contentRectMargins.right;
			linesDrawBufferTab[position][stopsToDrawIndex * 4 + 3] = rawValuesTab[position][stopsToDrawIndex];
		}
		linePaint.setColor(axis.getLineColor());
		canvas.drawLines(linesDrawBufferTab[position], 0, stopsToDrawIndex * 4, linePaint);
	}

	private void drawAxisVerticalLabels(Canvas canvas, Axis axis, int position) {
		for (int valueToDrawIndex = 0; valueToDrawIndex < valuesToDrawNumTab[position]; ++valueToDrawIndex) {
			int charsNumber = 0;
			if (axis.isAutoGenerated()) {
				final float value = autoValuesToDrawTab[position][valueToDrawIndex];
				charsNumber = axis.getFormatter().formatValueForAutoGeneratedAxis(labelBuffer, value,
						autoValuesBufferTab[position].decimals);
			} else {
				AxisValue axisValue = valuesToDrawTab[position][valueToDrawIndex];
				charsNumber = axis.getFormatter().formatValueForManualAxis(labelBuffer, axisValue);
			}

			if(axis.hasTiltedLabels()) {
				canvas.save();
				canvas.translate(tiltedLabelXTranslation[position], tiltedLabelYTranslation[position]);
				canvas.rotate(-45, labelBaselineTab[position], rawValuesTab[position][valueToDrawIndex]);
				canvas.drawText(labelBuffer, labelBuffer.length - charsNumber, charsNumber,
						labelBaselineTab[position], rawValuesTab[position][valueToDrawIndex],
						textPaintTab[position]);
				canvas.restore();
			}else{
				canvas.drawText(labelBuffer, labelBuffer.length - charsNumber, charsNumber,
						labelBaselineTab[position], rawValuesTab[position][valueToDrawIndex],
						textPaintTab[position]);
			}
		}

		// drawing axis name
		final Rect contentRectMargins = chart.getChartComputator().getContentRectMinusAxesMargins();
		if (!TextUtils.isEmpty(axis.getName())) {
			textPaintTab[position].setTextAlign(Align.CENTER);
			canvas.save();
			canvas.rotate(-90, contentRectMargins.centerY(), contentRectMargins.centerY());
			canvas.drawText(axis.getName(), contentRectMargins.centerY(), nameBaselineTab[position],
					textPaintTab[position]);
			canvas.restore();
		}
	}

	/**
	 * For axis inside chart area this method checks if there is place to draw axis label. If yes returns true,
	 * otherwise false.
	 */
	private boolean checkRawY(Rect rect, float rawY, boolean axisInside, int position) {
		if (axisInside) {
			float marginBottom = labelTextAscentTab[BOTTOM] + axisMargin;
			float marginTop = labelTextAscentTab[TOP] + axisMargin;
			if (rawY <= rect.bottom - marginBottom && rawY >= rect.top + marginTop) {
				return true;
			} else {
				return false;
			}
		}
		return true;
	}
}