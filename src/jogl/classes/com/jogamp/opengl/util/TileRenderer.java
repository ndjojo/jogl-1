/**
 * Copyright 2013 JogAmp Community. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 * 
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 * 
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY JogAmp Community ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JogAmp Community OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied, of JogAmp Community.
 * 
 * ---------------------
 * 
 * Based on Brian Paul's tile rendering library, found
 * at <a href = "http://www.mesa3d.org/brianp/TR.html">http://www.mesa3d.org/brianp/TR.html</a>.
 * 
 * Copyright (C) 1997-2005 Brian Paul. 
 * Licensed under BSD-compatible terms with permission of the author. 
 * See LICENSE.txt for license information.
 */
package com.jogamp.opengl.util;

import javax.media.nativewindow.util.Dimension;
import javax.media.opengl.GL;
import javax.media.opengl.GL2ES3;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLEventListener;

import com.jogamp.opengl.util.GLPixelBuffer.GLPixelAttributes;

/**
 * A fairly direct port of Brian Paul's tile rendering library, found
 * at <a href = "http://www.mesa3d.org/brianp/TR.html">
 * http://www.mesa3d.org/brianp/TR.html </a> . I've java-fied it, but
 * the functionality is the same.
 * <p>
 * Original code Copyright (C) 1997-2005 Brian Paul. Licensed under
 * BSD-compatible terms with permission of the author. See LICENSE.txt
 * for license information.
 * </p>
 * <p>
 * Enhanced for {@link GL2ES3}.
 * </p>
 * 
 * @author ryanm, sgothel
 */
public class TileRenderer {
    /**
     * The width of a tile
     */
    public static final int TR_TILE_WIDTH = 0;
    /**
     * The height of a tile
     */
    public static final int TR_TILE_HEIGHT = 1;
    /**
     * The width of the border around the tiles
     */
    public static final int TR_TILE_BORDER = 2;
    /**
     * The width of the final image
     */
    public static final int TR_IMAGE_WIDTH = 3;
    /**
     * The height of the final image
     */
    public static final int TR_IMAGE_HEIGHT = 4;
    /**
     * The number of rows of tiles
     */
    public static final int TR_ROWS = 5;
    /**
     * The number of columns of tiles
     */
    public static final int TR_COLUMNS = 6;
    /**
     * The current row number
     */
    public static final int TR_CURRENT_ROW = 7;
    /**
     * The current column number
     */
    public static final int TR_CURRENT_COLUMN = 8;
    /**
     * The width of the current tile
     */
    public static final int TR_CURRENT_TILE_WIDTH = 9;
    /**
     * The height of the current tile
     */
    public static final int TR_CURRENT_TILE_HEIGHT = 10;
    /**
     * The order that the rows are traversed
     */
    public static final int TR_ROW_ORDER = 11;
    /**
     * Indicates we are traversing rows from the top to the bottom
     */
    public static final int TR_TOP_TO_BOTTOM = 1;
    /**
     * Indicates we are traversing rows from the bottom to the top
     */
    public static final int TR_BOTTOM_TO_TOP = 2;

    private static final boolean DEBUG = true;
    private static final int DEFAULT_TILE_WIDTH = 256;
    private static final int DEFAULT_TILE_HEIGHT = 256;
    private static final int DEFAULT_TILE_BORDER = 0;

    private final Dimension imageSize = new Dimension(0, 0);
    private final Dimension tileSize = new Dimension(DEFAULT_TILE_WIDTH, DEFAULT_TILE_HEIGHT);
    private final Dimension tileSizeNB = new Dimension(DEFAULT_TILE_WIDTH - 2 * DEFAULT_TILE_BORDER, DEFAULT_TILE_HEIGHT - 2 * DEFAULT_TILE_BORDER);
    private final int[] userViewport = new int[ 4 ];
    private final GLPixelStorageModes psm = new GLPixelStorageModes();

    private int tileBorder = DEFAULT_TILE_BORDER;
    private GLPixelBuffer imageBuffer;
    private GLPixelBuffer tileBuffer;
    private int rowOrder = TR_BOTTOM_TO_TOP;
    private int rows;
    private int columns;
    private int currentTile = -1;
    private int currentTileWidth;
    private int currentTileHeight;
    private int currentRow;
    private int currentColumn;
    private PMVMatrixCallback pmvMatrixCB = null;
    private boolean beginCalled = false;

    private GLAutoDrawable glad;
    private GLEventListener[] listeners;
    private boolean[] listenersInit;
    private GLEventListener glEventListenerPre = null;
    private GLEventListener glEventListenerPost = null;
    
    public static interface PMVMatrixCallback {
        void reshapePMVMatrix(GL gl, int tileNum, int tileColumn, int tileRow, int tileX, int tileY, int tileWidth, int tileHeight, int imageWidth, int imageHeight);      
    }

    /**
     * Creates a new TileRenderer object
     */
    public TileRenderer() {
    }

    /**
     * Sets the size of the tiles to use in rendering. The actual
     * effective size of the tile depends on the border size, ie (
     * width - 2*border ) * ( height - 2 * border )
     * 
     * @param width
     *           The width of the tiles. Must not be larger than the GL
     *           context
     * @param height
     *           The height of the tiles. Must not be larger than the
     *           GL context
     * @param border
     *           The width of the borders on each tile. This is needed
     *           to avoid artifacts when rendering lines or points with
     *           thickness > 1.
     */
    public final void setTileSize(int width, int height, int border) {
        if( 0 > border ) {
            throw new IllegalArgumentException("Tile border must be >= 0");        
        }
        if( 2 * border >= width || 2 * border >= height ) {
            throw new IllegalArgumentException("Tile size must be > 0x0 minus 2*border");        
        }
        tileBorder = border;
        tileSize.setWidth( width );
        tileSize.setHeight( height );
        tileSizeNB.setWidth( width - 2 * border );
        tileSizeNB.setHeight( height - 2 * border );
        setup();
    }

    public final void setPMVMatrixCallback(PMVMatrixCallback pmvMatrixCB) {
        assert ( null != pmvMatrixCB );
        this.pmvMatrixCB = pmvMatrixCB; 
    }

    /**
     * Sets up the number of rows and columns needed
     */
    private final void setup() throws IllegalStateException {
        columns = ( imageSize.getWidth() + tileSizeNB.getWidth() - 1 ) / tileSizeNB.getWidth();
        rows = ( imageSize.getHeight() + tileSizeNB.getHeight() - 1 ) / tileSizeNB.getHeight();
        currentTile = 0;
        currentTileWidth = 0;
        currentTileHeight = 0;
        currentRow = 0;
        currentColumn = 0;

        assert columns >= 0;
        assert rows >= 0;
    }

    /** 
     * Returns <code>true</code> if all tiles have been rendered or {@link #setup()}
     * has not been called, otherwise <code>false</code>.
     */
    public final boolean eot() { return 0 > currentTile; }

    /**
     * Specify a buffer the tiles to be copied to. This is not
     * necessary for the creation of the final image, but useful if you
     * want to inspect each tile in turn.
     * 
     * @param buffer The buffer itself. Must be large enough to contain a tile, minus any borders
     */
    public final void setTileBuffer(GLPixelBuffer buffer) {
        tileBuffer = buffer;
    }

    /** @see #setTileBuffer(GLPixelBuffer) */
    public final GLPixelBuffer getTileBuffer() { return tileBuffer; }

    /**
     * Sets the desired size of the final image
     * 
     * @param width
     *           The width of the final image
     * @param height
     *           The height of the final image
     */
    public final void setImageSize(int width, int height) {
        imageSize.setWidth(width);
        imageSize.setHeight(height);
        setup();
    }

    /** @see #setImageSize(int, int) */
    public final Dimension getImageSize() { return imageSize; }
    
    /**
     * Sets the buffer in which to store the final image
     * 
     * @param image the buffer itself, must be large enough to hold the final image
     */
    public final void setImageBuffer(GLPixelBuffer buffer) {
        imageBuffer = buffer;
    }

    /** @see #setImageBuffer(GLPixelBuffer) */
    public final GLPixelBuffer getImageBuffer() { return imageBuffer; }
    
    /**
     * Gets the parameters of this TileRenderer object
     * 
     * @param param
     *           The parameter that is to be retrieved
     * @return the value of the parameter
     */
    public final int getParam(int param) {
        switch (param) {
        case TR_TILE_WIDTH:
            return tileSize.getWidth();
        case TR_TILE_HEIGHT:
            return tileSize.getHeight();
        case TR_TILE_BORDER:
            return tileBorder;
        case TR_IMAGE_WIDTH:
            return imageSize.getWidth();
        case TR_IMAGE_HEIGHT:
            return imageSize.getHeight();
        case TR_ROWS:
            return rows;
        case TR_COLUMNS:
            return columns;
        case TR_CURRENT_ROW:
            if( currentTile < 0 )
                return -1;
            else
                return currentRow;
        case TR_CURRENT_COLUMN:
            if( currentTile < 0 )
                return -1;
            else
                return currentColumn;
        case TR_CURRENT_TILE_WIDTH:
            return currentTileWidth;
        case TR_CURRENT_TILE_HEIGHT:
            return currentTileHeight;
        case TR_ROW_ORDER:
            return rowOrder;
        default:
            throw new IllegalArgumentException("Invalid enumerant as argument");
        }
    }

    /**
     * Sets the order of row traversal
     * 
     * @param order
     *           The row traversal order, must be
     *           eitherTR_TOP_TO_BOTTOM or TR_BOTTOM_TO_TOP
     */
    public final void setRowOrder(int order) {
        if (order == TR_TOP_TO_BOTTOM || order == TR_BOTTOM_TO_TOP) {
            rowOrder = order;
        } else {
            throw new IllegalArgumentException("Must pass TR_TOP_TO_BOTTOM or TR_BOTTOM_TO_TOP");
        }
    }

    /**
     * Begins rendering a tile.
     * <p> 
     * The projection matrix stack should be
     * left alone after calling this method!
     * </p>
     * 
     * @param gl The gl context
     * @throws IllegalStateException
     */
    public final void beginTile( GL2ES3 gl ) throws IllegalStateException {
        if( 0 >= imageSize.getWidth() || 0 >= imageSize.getHeight() ) {
            throw new IllegalStateException("Image size has not been set");        
        }
        if( null == this.pmvMatrixCB ) {
            throw new IllegalStateException("pmvMatrixCB has not been set");        
        }
        if (currentTile <= 0) {
            setup();
            /*
             * Save user's viewport, will be restored after last tile
             * rendered
             */
            gl.glGetIntegerv( GL.GL_VIEWPORT, userViewport, 0 );
        }

        final int preRow = currentRow;
        final int preColumn = currentColumn;

        /* which tile (by row and column) we're about to render */
        if (rowOrder == TR_BOTTOM_TO_TOP) {
            currentRow = currentTile / columns;
            currentColumn = currentTile % columns;
        } else {
            currentRow = rows - ( currentTile / columns ) - 1;
            currentColumn = currentTile % columns;
        }
        assert ( currentRow < rows );
        assert ( currentColumn < columns );

        int border = tileBorder;

        int tH, tW;

        /* Compute actual size of this tile with border */
        if (currentRow < rows - 1) {
            tH = tileSize.getHeight();
        } else {
            tH = imageSize.getHeight() - ( rows - 1 ) * ( tileSizeNB.getHeight() ) + 2 * border;
        }

        if (currentColumn < columns - 1) {
            tW = tileSize.getWidth();
        } else {
            tW = imageSize.getWidth() - ( columns - 1 ) * ( tileSizeNB.getWidth()  ) + 2 * border;
        }

        final int tX = currentColumn * tileSizeNB.getWidth();
        final int tY = currentRow * tileSizeNB.getHeight();

        final int preTileWidth = currentTileWidth;
        final int preTileHeight = currentTileHeight;

        /* Save tile size, with border */
        currentTileWidth = tW;
        currentTileHeight = tH;

        if( DEBUG ) {
            System.err.println("Tile["+currentTile+"]: ["+preColumn+"]["+preRow+"] "+preTileWidth+"x"+preTileHeight+
                    " -> ["+currentColumn+"]["+currentRow+"] "+tX+"/"+tY+", "+tW+"x"+tH+", image "+imageSize.getWidth()+"x"+imageSize.getHeight());
        }

        gl.glViewport( 0, 0, tW, tH );
        pmvMatrixCB.reshapePMVMatrix(gl, currentTile, currentColumn, currentRow, tX, tY, tW, tH, imageSize.getWidth(), imageSize.getHeight());
        beginCalled = true;
    }

    /**
     * Must be called after rendering the scene
     * 
     * @param gl
     *           the gl context
     * @return true if there are more tiles to be rendered, false if
     *         the final image is complete
     * @throws IllegalStateException
     */
    public boolean endTile( GL2ES3 gl ) throws IllegalStateException {
        if( !beginCalled ) {
            throw new IllegalStateException("beginTile(..) has not been called");
        }

        // be sure OpenGL rendering is finished
        gl.glFlush();

        // save current glPixelStore values
        psm.save(gl);

        final int tmp[] = new int[1];

        if( tileBuffer != null ) {
            final GLPixelAttributes pixelAttribs = tileBuffer.pixelAttributes;
            final int srcX = tileBorder;
            final int srcY = tileBorder;
            final int srcWidth = tileSizeNB.getWidth();
            final int srcHeight = tileSizeNB.getHeight();
            final int readPixelSize = GLBuffers.sizeof(gl, tmp, pixelAttribs.bytesPerPixel, srcWidth, srcHeight, 1, true);
            tileBuffer.clear();
            if( tileBuffer.requiresNewBuffer(gl, srcWidth, srcHeight, readPixelSize) ) {
                throw new IndexOutOfBoundsException("Required " + readPixelSize + " bytes of buffer, only had " + tileBuffer);
            }
            gl.glReadPixels( srcX, srcY, srcWidth, srcHeight, pixelAttribs.format, pixelAttribs.type, tileBuffer.buffer);
            // be sure OpenGL rendering is finished
            gl.glFlush();
            tileBuffer.position( readPixelSize );
            tileBuffer.flip();
        }

        if( imageBuffer != null ) {
            final GLPixelAttributes pixelAttribs = imageBuffer.pixelAttributes;
            final int srcX = tileBorder;
            final int srcY = tileBorder;
            final int srcWidth = currentTileWidth - 2 * tileBorder;
            final int srcHeight = currentTileHeight - 2 * tileBorder;

            /* setup pixel store for glReadPixels */
            final int rowLength = imageSize.getWidth();
            psm.setPackRowLength(gl, rowLength);
            psm.setPackAlignment(gl, 1);

            /* read the tile into the final image */
            final int readPixelSize = GLBuffers.sizeof(gl, tmp, pixelAttribs.bytesPerPixel, srcWidth, srcHeight, 1, true);

            final int skipPixels = currentColumn * tileSizeNB.getWidth();
            final int skipRows = currentRow * tileSizeNB.getHeight();
            final int ibPos = ( skipPixels + ( skipRows * rowLength ) ) * pixelAttribs.bytesPerPixel;
            final int ibLim = ibPos + readPixelSize;
            imageBuffer.clear();
            if( imageBuffer.requiresNewBuffer(gl, srcWidth, srcHeight, readPixelSize) ) {
                throw new IndexOutOfBoundsException("Required " + ibLim + " bytes of buffer, only had " + imageBuffer);
            }
            imageBuffer.position(ibPos);

            gl.glReadPixels( srcX, srcY, srcWidth, srcHeight, pixelAttribs.format, pixelAttribs.type, imageBuffer.buffer);
            // be sure OpenGL rendering is finished
            gl.glFlush();
            imageBuffer.position( ibLim );
            imageBuffer.flip();
        }

        /* restore previous glPixelStore values */
        psm.restore(gl);

        beginCalled = false;
        
        /* increment tile counter, return 1 if more tiles left to render */
        currentTile++;
        if( currentTile >= rows * columns ) {
            /* restore user's viewport */
            gl.glViewport( userViewport[ 0 ], userViewport[ 1 ], userViewport[ 2 ], userViewport[ 3 ] );
            currentTile = -1; /* all done */
            return false;
        } else {
            return true;
        }
    }
    
    /**
     * 
     * <p>
     * Sets the size of the tiles to use in rendering. The actual
     * effective size of the tile depends on the border size, ie (
     * width - 2*border ) * ( height - 2 * border )
     * </p>
     * @param glad
     * @param border
     *           The width of the borders on each tile. This is needed
     *           to avoid artifacts when rendering lines or points with
     *           thickness > 1.
     * @throws IllegalStateException if an {@link GLAutoDrawable} is already attached
     */
    public void attachAutoDrawable(GLAutoDrawable glad, int border, PMVMatrixCallback pmvMatrixCB) throws IllegalStateException {
        if( null != this.glad ) {
            throw new IllegalStateException("GLAutoDrawable already attached");
        }
        this.glad = glad;
        setTileSize(glad.getWidth(), glad.getHeight(), border);
        setPMVMatrixCallback(pmvMatrixCB);
        
        final int aSz = glad.getGLEventListenerCount();
        listeners = new GLEventListener[aSz];
        listenersInit = new boolean[aSz];
        for(int i=0; i<aSz; i++) {
            final GLEventListener l = glad.getGLEventListener(0);
            listenersInit[i] = glad.getGLEventListenerInitState(l);
            listeners[i] = glad.removeGLEventListener( l );
        }
        glad.addGLEventListener(tiledGLEL);
    }

    public void detachAutoDrawable() {
        if( null != glad ) {
            glad.removeGLEventListener(tiledGLEL);
            final int aSz = listenersInit.length;
            for(int i=0; i<aSz; i++) {
                final GLEventListener l = listeners[i];
                glad.addGLEventListener(l);
                glad.setGLEventListenerInitState(l, listenersInit[i]);
            }
            listeners = null;
            listenersInit = null;
            glad = null;
            pmvMatrixCB = null;
        }
    }

    /**
     * Set {@link GLEventListener} for pre- and post operations when used w/ 
     * {@link #attachAutoDrawable(GLAutoDrawable, int, PMVMatrixCallback)}
     * for each {@link GLEventListener} callback.
     * @param preTile the pre operations
     * @param postTile the post operations
     */
    public void setGLEventListener(GLEventListener preTile, GLEventListener postTile) {
        glEventListenerPre = preTile;
        glEventListenerPost = postTile;
    }
    
    /**
     * Rendering one tile, by simply calling {@link GLAutoDrawable#display()}.
     * 
     * @return true if there are more tiles to be rendered, false if the final image is complete
     * @throws IllegalStateException if no {@link GLAutoDrawable} is {@link #attachAutoDrawable(GLAutoDrawable, int) attached}
     *                               or imageSize is not set
     */
    public boolean display() throws IllegalStateException {
        if( null == glad ) {
            throw new IllegalStateException("No GLAutoDrawable attached");
        }
        glad.display();
        return !eot();
    }

    private final GLEventListener tiledGLEL = new GLEventListener() {
        @Override
        public void init(GLAutoDrawable drawable) {
            if( null != glEventListenerPre ) {
                glEventListenerPre.init(drawable);
            }
            final int aSz = listenersInit.length;
            for(int i=0; i<aSz; i++) {
                final GLEventListener l = listeners[i];
                l.init(drawable);
                listenersInit[i] = true;
            }
            if( null != glEventListenerPost ) {
                glEventListenerPost.init(drawable);
            }
        }
        @Override
        public void dispose(GLAutoDrawable drawable) {
            if( null != glEventListenerPre ) {
                glEventListenerPre.dispose(drawable);
            }
            final int aSz = listenersInit.length;
            for(int i=0; i<aSz; i++) {
                listeners[i].dispose(drawable);
            }
            if( null != glEventListenerPost ) {
                glEventListenerPost.dispose(drawable);
            }
        }
        @Override
        public void display(GLAutoDrawable drawable) {
            if( null != glEventListenerPre ) {
                glEventListenerPre.display(drawable);
            }
            final GL2ES3 gl = drawable.getGL().getGL2ES3();

            beginTile(gl);

            final int aSz = listenersInit.length;
            for(int i=0; i<aSz; i++) {
                listeners[i].display(drawable);
            }

            endTile(gl);
            if( null != glEventListenerPost ) {
                glEventListenerPost.display(drawable);
            }
        }
        @Override
        public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
            if( null != glEventListenerPre ) {
                glEventListenerPre.reshape(drawable, x, y, width, height);
            }
            final int aSz = listenersInit.length;
            for(int i=0; i<aSz; i++) {
                listeners[i].reshape(drawable, x, y, width, height);
            }
            if( null != glEventListenerPost ) {
                glEventListenerPost.reshape(drawable, x, y, width, height);
            }
        }
    };    
}