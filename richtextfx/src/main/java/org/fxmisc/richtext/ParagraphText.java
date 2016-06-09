package org.fxmisc.richtext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.IndexRange;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.StrokeLineCap;

import org.fxmisc.richtext.model.Paragraph;
import org.fxmisc.richtext.model.Segment;
import org.reactfx.value.Val;
import org.reactfx.value.Var;

class ParagraphText<PS, S> extends TextFlowExt {

    // FIXME: changing it currently has not effect, because
    // Text.impl_selectionFillProperty().set(newFill) doesn't work
    // properly for Text node inside a TextFlow (as of JDK8-b100).
    private final ObjectProperty<Paint> highlightTextFill = new SimpleObjectProperty<>(Color.WHITE);
    public ObjectProperty<Paint> highlightTextFillProperty() {
        return highlightTextFill;
    }

    private final Var<Integer> caretPosition = Var.newSimpleVar(0);
    public Var<Integer> caretPositionProperty() { return caretPosition; }
    public void setCaretPosition(int pos) { caretPosition.setValue(pos); }
    private final Val<Integer> clampedCaretPosition;

    private final ObjectProperty<IndexRange> selection = new SimpleObjectProperty<>(StyledTextArea.EMPTY_RANGE);
    public ObjectProperty<IndexRange> selectionProperty() { return selection; }
    public void setSelection(IndexRange sel) { selection.set(sel); }

    private final Paragraph<PS, S> paragraph;

    private final Path caretShape = new Path();
    private final Path selectionShape = new Path();
    private final List<Path> backgroundShapes = new ArrayList<>();
    private final List<Path> underlineShapes = new ArrayList<>();

    // proxy for caretShape.visibleProperty() that implements unbind() correctly.
    // This is necessary due to a bug in BooleanPropertyBase#unbind().
    // See https://bugs.openjdk.java.net/browse/JDK-8130458
    private final Var<Boolean> caretVisible = Var.newSimpleVar(false);

//    private Map<Integer, SegmentFactory> factories = new HashMap<>();


    {
        caretShape.visibleProperty().bind(caretVisible);
    }

    ParagraphText(Paragraph<PS, S> par, BiConsumer<? super TextExt, S> applyStyle,
                  BiFunction <? super Segment<S>, 
                              BiConsumer<? super TextExt, S>, Node> nodeFactory) {
        this.paragraph = par;

        getStyleClass().add("paragraph-text");

        int parLen = paragraph.length();
        clampedCaretPosition = caretPosition.map(i -> Math.min(i, parLen));
        clampedCaretPosition.addListener((obs, oldPos, newPos) -> requestLayout());

        selection.addListener((obs, old, sel) -> requestLayout());

        Val<Double> leftInset = Val.map(insetsProperty(), Insets::getLeft);
        Val<Double> topInset = Val.map(insetsProperty(), Insets::getTop);

        // selection highlight
        selectionShape.setManaged(false);
        selectionShape.setFill(Color.DODGERBLUE);
        selectionShape.setStrokeWidth(0);
        selectionShape.layoutXProperty().bind(leftInset);
        selectionShape.layoutYProperty().bind(topInset);
        getChildren().add(selectionShape);

        // caret
        caretShape.getStyleClass().add("caret");
        caretShape.setManaged(false);
        caretShape.setStrokeWidth(1);
        caretShape.layoutXProperty().bind(leftInset);
        caretShape.layoutYProperty().bind(topInset);
        getChildren().add(caretShape);

        // XXX: see the note at highlightTextFill
//        highlightTextFill.addListener(new ChangeListener<Paint>() {
//            @Override
//            public void changed(ObservableValue<? extends Paint> observable,
//                    Paint oldFill, Paint newFill) {
//                for(PumpedUpText text: textNodes())
//                    text.impl_selectionFillProperty().set(newFill);
//            }
//        });

        // populate with nodes
        for(Segment<S> segment: par.getSegments()) {

            // Create the object node
            Node t = nodeFactory.apply(segment, applyStyle);
            // Node t = createNode(segment, applyStyle);
            getChildren().add(t);

            // add corresponding background node (empty)
            Path backgroundShape = new Path();
            backgroundShape.setManaged(false);
            backgroundShape.setStrokeWidth(0);
            backgroundShape.layoutXProperty().bind(leftInset);
            backgroundShape.layoutYProperty().bind(topInset);
            backgroundShapes.add(backgroundShape);
            getChildren().add(0, backgroundShape);

            // add corresponding underline node (empty)
            Path underlineShape = new Path();
            underlineShape.setManaged(false);
            underlineShape.setStrokeWidth(0);
            underlineShape.layoutXProperty().bind(leftInset);
            underlineShape.layoutYProperty().bind(topInset);
            underlineShapes.add(underlineShape);
            getChildren().add(0, underlineShape);
        }
    }
//
//
//    private Node createNode(Segment<S> segment, BiConsumer<? super TextExt, S> applyStyle) {
//        SegmentFactory factory = factories.get(segment.getTypeId());
//        if (factory == null) {
//            throw new RuntimeException("No factory for type " + segment.getTypeId());
//        }
//
//        if (segment instanceof CustomObject) {
//            CustomObject<S> customObject = (CustomObject<S>) segment;
//            ObjectData objData = customObject.getObjectData();
//            String imagePath = objData.getData();
//            Image image = new Image(imagePath); // TODO: No need to create new Image objects each time -
//                                                // can be stored in the model layer (ObjectData)
//
//            return new ImageView(image);
//        } else {
//            TextExt t = new TextExt(segment.getText());
//            t.setTextOrigin(VPos.TOP);
//            t.getStyleClass().add("text");
//            applyStyle.accept(t, segment.getStyle());
//
//            // XXX: binding selectionFill to textFill,
//            // see the note at highlightTextFill
//            t.impl_selectionFillProperty().bind(t.fillProperty());
//
//            return t;
//        }
//    }

    public Paragraph<PS, S> getParagraph() {
        return paragraph;
    }

    public Var<Boolean> caretVisibleProperty() {
        return caretVisible;
    }

    public ObjectProperty<Paint> highlightFillProperty() {
        return selectionShape.fillProperty();
    }

    public double getCaretOffsetX() {
        layout(); // ensure layout, is a no-op if not dirty
        Bounds bounds = caretShape.getLayoutBounds();
        return (bounds.getMinX() + bounds.getMaxX()) / 2;
    }

    public Bounds getCaretBounds() {
        layout(); // ensure layout, is a no-op if not dirty
        return caretShape.getBoundsInParent();
    }

    public Bounds getCaretBoundsOnScreen() {
        layout(); // ensure layout, is a no-op if not dirty
        Bounds localBounds = caretShape.getBoundsInLocal();
        return caretShape.localToScreen(localBounds);
    }

    public Optional<Bounds> getSelectionBoundsOnScreen() {
        if(selection.get().getLength() == 0) {
            return Optional.empty();
        } else {
            layout(); // ensure layout, is a no-op if not dirty
            Bounds localBounds = selectionShape.getBoundsInLocal();
            return Optional.of(selectionShape.localToScreen(localBounds));
        }
    }

    public int currentLineIndex() {
        return getLineOfCharacter(clampedCaretPosition.getValue());
    }

    private void updateCaretShape() {
        PathElement[] shape = getCaretShape(clampedCaretPosition.getValue(), true);
        caretShape.getElements().setAll(shape);
    }

    private void updateSelectionShape() {
        int start = selection.get().getStart();
        int end = selection.get().getEnd();
        PathElement[] shape = getRangeShape(start, end);
        selectionShape.getElements().setAll(shape);
    }

    private void updateBackgroundShapes() {
        int index = 0;
        int start = 0;

        FilteredList<Node> nodeList = getChildren().filtered(node -> node instanceof TextExt);
        for (Node node : nodeList) {
            TextExt text = (TextExt) node;
            int end = start + text.getText().length();

            updateBackground(text, start, end, index);
            updateUnderline(text, start, end, index);

            start = end;
            index++;
        }
    }


    /**
     * Updates the background shape for a text segment.
     *
     * @param text  The text node which specified the style attributes
     * @param start The index of the first character 
     * @param end   The index of the last character
     * @param index The index of the background shape
     */
    private void updateBackground(TextExt text, int start, int end, int index) {
        // Set fill
        Paint paint = text.backgroundFillProperty().get();
        if (paint != null) {
            Path backgroundShape = backgroundShapes.get(index);
            backgroundShape.setFill(paint);

            // Set path elements
            PathElement[] shape = getRangeShape(start, end);
            backgroundShape.getElements().setAll(shape);
        }
    }


    /**
     * Updates the shape which renders the text underline.
     * 
     * @param text  The text node which specified the style attributes
     * @param start The index of the first character 
     * @param end   The index of the last character
     * @param index The index of the background shape
     */
    private void updateUnderline(TextExt text, int start, int end, int index) {

        // get all CSS properties for the underline

        Paint underlineColor = text.underlineColorProperty().get();
        Number underlineWidth = text.underlineWidthProperty().get();

        // get the dash array - JavaFX CSS parser seems to return either a Number[] array
        // or a single value, depending on whether only one or more than one value has been
        // specified in the CSS
        Double[] underlineDashArray = null;
        Object underlineDashArrayProp = text.underlineDashArrayProperty().get();
        if (underlineDashArrayProp != null) {
            if (underlineDashArrayProp.getClass().isArray()) {
                Number[] numberArray = (Number[]) underlineDashArrayProp;
                underlineDashArray = new Double[numberArray.length];
                int idx = 0;
                for (Number d : numberArray) {
                    underlineDashArray[idx++] = (Double) d;
                }
            } else {
                underlineDashArray = new Double[1];
                underlineDashArray[0] = ((Double) underlineDashArrayProp).doubleValue();
            }
        }

        StrokeLineCap underlineCap = text.underlineCapProperty().get();

        // apply style and render the underline

        Path underlineShape = underlineShapes.get(index);
        if (underlineColor != null) {
            underlineShape.setStroke(underlineColor);
        }
        if (underlineWidth != null) {
            underlineShape.setStrokeWidth(underlineWidth.doubleValue());
        }
        if (underlineDashArray != null) {
            underlineShape.getStrokeDashArray().addAll(underlineDashArray);
            underlineShape.setStrokeLineCap(StrokeLineCap.BUTT);
        }
        if (underlineCap != null) {
            underlineShape.setStrokeLineCap(underlineCap);
        }

        if (underlineColor != null || underlineWidth != null) {
            
            // Set path elements
            PathElement[] shape = getUnderlineShape(start, end);
            underlineShape.getElements().setAll(shape);
        }
    }


    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        updateCaretShape();
        updateSelectionShape();
        updateBackgroundShapes();
    }
}
