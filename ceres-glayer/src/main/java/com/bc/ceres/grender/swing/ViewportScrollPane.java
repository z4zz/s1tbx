/*
 * $Id: ViewPane.java,v 1.2 2006/10/23 06:38:14 norman Exp $
 *
 * Copyright (C) 2002 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.bc.ceres.grender.swing;

import com.bc.ceres.core.Assert;
import com.bc.ceres.grender.Viewport;
import com.bc.ceres.grender.ViewportListener;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Rectangle2D;

// todo - find better name

/**
 * A <code>ViewPane</code> is an alternative to {@link javax.swing.JScrollPane}
 * when you need to scroll an infinite area given in floating-point coordinates.
 * <p/>
 * In opposite to {@link javax.swing.JScrollPane}, we don't scroll a view  given
 * by a {@link javax.swing.JComponent} but it's {@link Viewport}. For this reason
 * the view component must implement the {@link AdjustableView} interface.
 *
 * @author Norman Fomferra (norman.fomferra@brockmann-consult.de)
 * @version $Revision: 2182 $ $Date: 2008-06-12 11:09:11 +0200 (Thu, 12 Jun 2008) $
 */
public class ViewportScrollPane extends JComponent {
    private static final long serialVersionUID = -2634482999458990218L;

    // Extension of model bounds in view coordinates
    private static final int MODEL_BOUNDS_EXTENSION = 10; // pixels
    // Maximum scroll bar value
    private static final int MAX_SB_VALUE = 10000;

    private JScrollBar horizontalScrollBar;
    private JScrollBar verticalScrollBar;
    private JComponent cornerComponent;
    private JComponent viewComponent;
    private AdjustableView adjustableView;
    private Rectangle2D scrollArea;
    private boolean updatingScrollBars;
    private boolean scrollBarsUpdated;
    private ViewportChangeHandler viewportChangeHandler;
    private boolean debug = true;

    /**
     * Constructs a new view pane with an empty view component.
     */
    public ViewportScrollPane() {
        this(null);
    }

    /**
     * Constructs a new view pane with the given view viewComponent
     *
     * @param viewComponent the view viewComponent. If not null, it must implement {@link AdjustableView}.
     * 
     */
    public ViewportScrollPane(JComponent viewComponent) {
        Assert.notNull(viewComponent, "viewComponent");
        Assert.argument(viewComponent instanceof AdjustableView, "viewComponent");
        scrollArea = new Rectangle2D.Double();
        viewportChangeHandler = new ViewportChangeHandler();
        setOpaque(false);
        setLayout(null);
        setViewComponent(viewComponent);
        setCornerComponent(createCornerComponent());
        final ChangeListener scrollBarCH = new ScrollBarChangeHandler();
        horizontalScrollBar = createHorizontalScrollbar();
        horizontalScrollBar.setVisible(false);
        horizontalScrollBar.getModel().addChangeListener(scrollBarCH);
        verticalScrollBar = createVerticalScrollBar();
        verticalScrollBar.setVisible(false);
        verticalScrollBar.getModel().addChangeListener(scrollBarCH);
        add(horizontalScrollBar);
        add(verticalScrollBar);
        addComponentListener(new ViewPaneResizeHandler());
        scrollBarsUpdated = false;
    }

    public JComponent getViewComponent() {
        return viewComponent;
    }

    /**
     * Constructs a new view pane with the given view viewComponent
     *
     * @param viewComponent the view viewComponent. If not null, it must implement {@link AdjustableView}.
     */
    public void setViewComponent(JComponent viewComponent) {
        if (this.adjustableView != null) {
            this.adjustableView.getViewport().removeListener(viewportChangeHandler);
        }
        if (this.viewComponent != null) {
            remove(this.viewComponent);
        }
        this.viewComponent = viewComponent;
        this.adjustableView = null;
        if (this.viewComponent != null) {
            this.adjustableView = (AdjustableView) viewComponent;
            add(this.viewComponent);
        }
        if (this.adjustableView != null) {
            this.adjustableView.getViewport().addListener(viewportChangeHandler);
        }
        revalidate();
        validate();
    }

    public JComponent getCornerComponent() {
        return cornerComponent;
    }

    public void setCornerComponent(JComponent cornerComponent) {
        if (this.cornerComponent != null) {
            remove(this.cornerComponent);
        }
        this.cornerComponent = cornerComponent;
        if (this.cornerComponent != null) {
            add(this.cornerComponent);
        }
        revalidate();
        validate();
    }

    @Override
    public void doLayout() {
        if (viewComponent == null || !viewComponent.isVisible()) {
            return;
        }
        if (!scrollBarsUpdated) {
            updateScrollBars();
            updateScrollBarIncrements();
        }

        final Insets insets = getInsets();
        final int width = getWidth() - (insets.left + insets.right);
        final int height = getHeight() - (insets.top + insets.bottom);
        if (width <= 0 || height <= 0) {
            return;
        }

        // x1              w1              x2 w2
        // +-------------------------------+---+ y1
        // |                               |   |
        // |                               | v |
        // |                               | s |
        // |                               | b |
        // |             view              |   | h1
        // |                               |   |
        // |                               |   |
        // |                               |   |
        // |                               |   |
        // +-------------------------------+---+ y2
        // |             hsb               |   | h2
        // +-------------------------------+---+
        //
        final boolean hsbVisible = horizontalScrollBar.isVisible();
        final boolean vsbVisible = verticalScrollBar.isVisible();
        if (hsbVisible && vsbVisible) {
            final Dimension hsbSize = horizontalScrollBar.getPreferredSize();
            final Dimension vsbSize = verticalScrollBar.getPreferredSize();
            final int x1 = insets.left;
            final int y1 = insets.top;
            final int w2 = vsbSize.width;
            final int h2 = hsbSize.height;
            final int w1 = width - w2;
            final int h1 = height - h2;
            final int x2 = x1 + w1;
            final int y2 = y1 + h1;
            viewComponent.setBounds(x1, y1, w1, h1);
            verticalScrollBar.setBounds(x2, y1, w2, h1);
            horizontalScrollBar.setBounds(x1, y2, w1, h2);
            if (cornerComponent != null) {
                cornerComponent.setBounds(x2, y2, w2, h2);
            }
        } else if (hsbVisible) {
            final Dimension hsbSize = horizontalScrollBar.getPreferredSize();
            final int x1 = insets.left;
            final int y1 = insets.top;
            final int w1 = width;
            final int h2 = hsbSize.height;
            final int h1 = height - h2;
            final int y2 = y1 + h1;
            viewComponent.setBounds(x1, y1, w1, h1);
            horizontalScrollBar.setBounds(x1, y2, w1, h2);
        } else if (vsbVisible) {
            final Dimension vsbSize = verticalScrollBar.getPreferredSize();
            final int x1 = insets.left;
            final int y1 = insets.top;
            final int w2 = vsbSize.width;
            final int w1 = width - w2;
            final int h1 = height;
            final int x2 = x1 + w1;
            viewComponent.setBounds(x1, y1, w1, h1);
            verticalScrollBar.setBounds(x2, y1, w2, h1);
        } else {
            final int x1 = insets.left;
            final int y1 = insets.top;
            viewComponent.setBounds(x1, y1, width, height);
        }

        viewComponent.doLayout();
    }

    @Override
    protected void addImpl(Component comp, Object constraints, int index) {
        if (comp != horizontalScrollBar &&
                comp != verticalScrollBar &&
                comp != cornerComponent &&
                comp != viewComponent) {
            throw new IllegalArgumentException();
        }
        super.addImpl(comp, constraints, index);
    }

    /**
     * @return <code>new JScrollBar(JScrollBar.HORIZONTAL)</code>
     */
    protected JScrollBar createHorizontalScrollbar() {
        return new JScrollBar(JScrollBar.HORIZONTAL);
    }

    /**
     * @return <code>new JScrollBar(JScrollBar.VERTICAL)</code>
     */
    protected JScrollBar createVerticalScrollBar() {
        return new JScrollBar(JScrollBar.VERTICAL);
    }

    /**
     * @return <code>new JPanel()</code>
     */
    protected JPanel createCornerComponent() {
        return new JPanel();
    }


    private void updateViewport() {
        if (updatingScrollBars || adjustableView == null) {
            if (debug) {
                System.out.println("ViewportScrollPane.updateViewport: return!");
            }
            return;
        }

        final Rectangle va = getViewBounds();
        double vx = va.getX();
        double vy = va.getY();
        final boolean hsbVisible = horizontalScrollBar.isVisible();
        final boolean vsbVisible = verticalScrollBar.isVisible();
        final Rectangle2D sa = scrollArea;
        if (hsbVisible) {
            final int hsbValue = horizontalScrollBar.getValue();
            vx = sa.getX() + hsbValue * sa.getWidth() / MAX_SB_VALUE;
        }
        if (vsbVisible) {
            final int vsbValue = verticalScrollBar.getValue();
            vy = sa.getY() + vsbValue * sa.getHeight() / MAX_SB_VALUE;
        }
        if (hsbVisible || vsbVisible) {
            if (debug) {
                System.out.println("ViewportScrollPane.updateViewport:");
                System.out.println("  vx = " + vx);
                System.out.println("  vy = " + vy);
                System.out.println("");
            }
            adjustableView.getViewport().moveDelta(-vx, -vy);
        }
    }

    private void updateScrollBars() {
        if (adjustableView == null) {
            if (debug) {
                System.out.println("ViewportScrollPane.updateScrollBars: return!");
            }
            return;
        }

        //.View bounds in view coordinates
        final Rectangle2D va = getViewBounds();
        if (va.isEmpty()) {
            horizontalScrollBar.setVisible(false);
            verticalScrollBar.setVisible(false);
            return;
        }

        // Model bounds in view coordinates
        final Rectangle2D ma = adjustableView.getViewport().getModelToViewTransform().createTransformedShape(adjustableView.getModelBounds()).getBounds2D();
        // Following code make it easier to scroll out of the model area
        ma.add(ma.getX() - MODEL_BOUNDS_EXTENSION, ma.getY() - MODEL_BOUNDS_EXTENSION);
        ma.add(ma.getX() + ma.getWidth() + MODEL_BOUNDS_EXTENSION, ma.getY() + ma.getHeight() + MODEL_BOUNDS_EXTENSION);

        // Scroll bounds in view coordinates
        final Rectangle2D sa = ma.createUnion(va);

        //  x1,x2,y1,y2(+)   no scrollbars         x1,x2,y1,y2(-)  V+H-scrollbars
        //  +--------------------------------+     +--------------------------------+
        //  | va             ^               |     | ma             ^               |
        //  |                |               |     |                |               |
        //  |                y1              |     |                y1              |
        //  |                |               |     |                |               |
        //  |                v               |     |                v               |
        //  |        +--------------+        |     |        +--------------+        |
        //  |        | ma           |        |     |        | va           V        |
        //  |<--x1-->|              |<--x2-->|     |<--x1-->|              V<--x2-->|
        //  |        |              |        |     |        |              V        |
        //  |        +--------------+        |     |        +HHHHHHHHHHHHHH+        |
        //  |                ^               |     |                ^               |
        //  |                |               |     |                |               |
        //  |                y2              |     |                y2              |
        //  |                |               |     |                |               |
        //  |                v               |     |                v               |
        //  +--------------------------------+     +--------------------------------+
        //
        //  x1,y1,y2(+) x2(-)  H-scrollbar                 x1,y1,y2(+) x2(-)  H,V-scrollbars
        //  +--------------------------------+             +--------------------------------+
        //  | va                     ^       |             | ma                     ^       |
        //  |                        |       |             |                        |       |
        //  |                        y1      |             |                        y1      |
        //  |                        |       |<--x2-->     |                        |       |<--x2-->
        //  |                        v       |             |                        v       |
        //  |                     +-------------------+    |                     +-------------------+
        //  |                     | ma                |    |                     | va                V
        //  |<---------x1-------->|                   |    |<---------x1-------->|                   V
        //  |                     |                   |    |                     |                   V
        //  |                     +-------------------+    |                     +HHHHHHHHHHHHHHHHHHH+
        //  |                        ^       |             |                        ^       |
        //  |                        |       |             |                        |       |
        //  |                        y2      |             |                        y2      |
        //  |                        |       |             |                        |       |
        //  |                        v       |             |                        v       |
        //  +HHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHH+             +--------------------------------+
        //
        final double dx1 = ma.getX() - va.getX();
        final double dy1 = ma.getY() - va.getY();
        final double dx2 = (va.getX() + va.getWidth()) - (ma.getX() + ma.getWidth());
        final double dy2 = (va.getY() + va.getHeight()) - (ma.getY() + ma.getHeight());

        boolean hsbVisible = dx1 < 0 || dx2 < 0;
        boolean vsbVisible = dy1 < 0 || dy2 < 0;

        if (debug) {
            System.out.println("ViewportScrollPane.updateScrollBars:");
            System.out.println("  hsbVisible = " + vsbVisible);
            System.out.println("  hsbVisible = " + hsbVisible);
            System.out.println("  va = " + va);
            System.out.println("  ma = " + ma);
            System.out.println("  sa = " + sa);
            System.out.println("  dx1 = " + dx1 + ", dx2 = " + dx2);
            System.out.println("  dy1 = " + dy1 + ", dy2 = " + dy2);
            System.out.println("  ");
        }

        scrollArea.setRect(sa);

        updatingScrollBars = true;

        if (hsbVisible) {
            int hsbValue = (int) Math.round(MAX_SB_VALUE * (va.getX() - sa.getX()) / sa.getWidth());
            hsbValue = clamp(hsbValue, 0, MAX_SB_VALUE);
            int hsbExtend = (int) Math.round(MAX_SB_VALUE * va.getWidth() / sa.getWidth());
            hsbExtend = clamp(hsbExtend, 0, MAX_SB_VALUE);
            horizontalScrollBar.setValues(hsbValue, hsbExtend, 0, MAX_SB_VALUE);
        }
        horizontalScrollBar.setVisible(hsbVisible);

        if (vsbVisible) {
            int vsbValue = (int) Math.round(MAX_SB_VALUE * (va.getY() - sa.getY()) / sa.getHeight());
            vsbValue = clamp(vsbValue, 0, MAX_SB_VALUE);
            int vsbExtend = (int) Math.round(MAX_SB_VALUE * va.getHeight() / sa.getHeight());
            vsbExtend = clamp(vsbExtend, 0, MAX_SB_VALUE);
            verticalScrollBar.setValues(vsbValue, vsbExtend, 0, MAX_SB_VALUE);
        }
        verticalScrollBar.setVisible(vsbVisible);

        if (cornerComponent != null) {
            cornerComponent.setVisible(hsbVisible && vsbVisible);
        }

        updatingScrollBars = false;
        scrollBarsUpdated = true;
    }

    private void updateScrollBarIncrements() {
        // we could set more reasonable increments at this place, e.g. using the viewport.modelArea property
        horizontalScrollBar.setUnitIncrement(Math.max(10, MAX_SB_VALUE / 50));
        horizontalScrollBar.setBlockIncrement(Math.max(10, MAX_SB_VALUE / 5));
        verticalScrollBar.setUnitIncrement(Math.max(10, MAX_SB_VALUE / 50));
        verticalScrollBar.setBlockIncrement(Math.max(10, MAX_SB_VALUE / 5));
    }

    private Rectangle getViewBounds() {
        return new Rectangle(0, 0, viewComponent.getWidth(), viewComponent.getHeight());
    }

    private static int clamp(int value, int min, int max) {
        return (value < min) ? min : (value > max) ? max : value;
    }

    private class ScrollBarChangeHandler implements ChangeListener {
        public void stateChanged(ChangeEvent e) {
            updateViewport();
        }
    }

    private class ViewPaneResizeHandler extends ComponentAdapter {
        @Override
        public void componentResized(ComponentEvent e) {
            updateScrollBars();
        }
    }

    private class ViewportChangeHandler implements ViewportListener {
        public void handleViewportChanged(Viewport viewport, boolean orientationChanged) {
            updateScrollBars();
            updateScrollBarIncrements();
        }
    }

}