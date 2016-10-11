package trb.jme.imaging;

/**
 * Copyright (c) 2003, Xith3D Project Group
 * All rights reserved.
 *
 * Portions based on the Java3D interface, Copyright by Sun Microsystems.
 * Many thanks to the developers of Java3D and Sun Microsystems for their
 * innovation and design.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the 'Xith3D Project Group' nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) A
 * RISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE
 *
 */

//import com.xith3d.utility.logs.*;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This object manages all the textures needed by the program.  Textures
 * can be retrieved by name.  Multiple paths for actual image files can
 * be registered with the factory.  There are plans to allow the texture
 * factory to be streamed to disk for faster re-loading.
 *
 * @author David Yazel
 *
 */
public class TextureLoader {

    public static final int SCALE_BOX = 1;
    public static final int SCALE_DRAW_FAST = 2;
    public static final int SCALE_DRAW_GOOD = 3;
    public static final int SCALE_DRAW_BEST = 4;

    public static boolean usePNG = true;
    public static TextureLoader tf = new TextureLoader();
    
    /**
     * The user data key object for the file name meta data of the 
     * ImageComponent objects in a Texture object. 
     *
     * Note that Object-type key is used to guarantee that conventional 
     * (old) user data namespace will never intersect with other user-defined namespaces.
     */
    public static final Object XITH3D_USERDATAKEY_ICFILENAME = new Object();

    // for creating mipmaps

    private static ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
    private static int[] nBits = {
        8, 8, 8, 8};
    private static int[] bandOffset = {
        0, 1, 2, 3};

    private static ComponentColorModel colorModel = new ComponentColorModel(cs,
        nBits, true, false, Transparency.TRANSLUCENT, 0);

    Hashtable tab;
    Hashtable images;
    Vector path;
    Vector jarPath;
    String cachePath = null;

    public static TextureLoader getInstance() {
        return tf;
    }

    /**
     * This constructor takes a AWT component because the TextureLoader
     * requires one.
     */
    public TextureLoader() {
        tab = new Hashtable(100);
        images = new Hashtable(100);
        path = new Vector(10);
        jarPath = new Vector(10);
        registerJarPath("/"); // registers the default Jar path
    }

    /**
     * if a cache path is set then the first time you load a texture it will
     * be saved off into a big file which can be memory mapped for
     * super fast loading speed the second time around.
     * @param path
     */
    public void setCachePath(String path) {
        cachePath = path;
    }
    /**
     * This will register a path for the texture loader to search when
     * attemping to locate textures.  Each path should end with a /
     */
    public void registerPath(String name) {
        char lastChar = name.charAt(name.length() - 1);

        if ( (lastChar == '/') || (lastChar == '\\') ||
            name.endsWith(File.separator)) {
            path.addElement(name);
        } else {
            path.addElement(name + File.separator);
        }
    }

    /**
     * This will register a URL path for the texture loader to search when
     * attemping to locate textures.
     */
    public void registerJarPath(String jarName) {
        jarPath.addElement(jarName);
    }


    public String findImageFile(String name) {
        if ( (!usePNG) && (name.toLowerCase().endsWith(".png"))) {
            System.out.println("Converting PNG " + name);
            name = name.substring(0, name.lastIndexOf('.')) + ".jpg";
        }

        // check to see if the image name is already fully qualified.
        if ((name.indexOf("/") >= 0) || (name.indexOf("\\")>=0)) {
            if (new File(name).isFile()) {
                return name;
            }
        }

        //accumulate search path to print as part of error msg if file not found
        StringBuffer searchPath = new StringBuffer();

        for (int i = 0; i < path.size(); i++) {
            String filename = (String) path.elementAt(i);
            filename += name;

            searchPath.append(filename);
            searchPath.append(";");

            File f = new File(filename);

            if (f.exists()) {
                return filename;
            }
        }

        //System.out.println("Cannot find image file in texture loader : "+name+", in path : "+searchPath);
        return null;
    }
    
    public URL findImageFileJar (String name) {

        //accumulate search path to print as part of error msg if file not found
        StringBuffer searchPath = new StringBuffer();

        for (int i = 0; i < jarPath.size(); i++) {
            String filename = (String) jarPath.elementAt(i);
            filename += name;

            searchPath.append(filename);
            searchPath.append(";");

            URL imageURL = this.getClass().getResource(filename);
            if (imageURL != null)
                return imageURL;
        }
        
        //System.out.println("Cannot find image file in texture loader : "+name+", in path : "+searchPath);

        return null;
    }

    public String getSearchPath() {
        //accumulate search path to print as part of error msg if file not found
        StringBuffer searchPath = new StringBuffer();

        for (int i = 0; i < path.size(); i++) {
            String filename = (String) path.elementAt(i);

            searchPath.append(filename);
            searchPath.append(";");
        }

        return searchPath.toString();
    }

    public String resolvePath(String name) {

        if (name.indexOf("\\")>0) return name;
        String filename = findImageFile(name);

        if (filename == null) {
        	//System.out.println("Cannot find texture: " + name + " using search path " + getSearchPath());
            throw new Error("Cannot find image " + name);
        }

        return filename;
    }

    /**
     * A helper method that sets the file name meta data for the 
     * ImageComponent objects of a Texture.
     *  
     * @param tex The Texture object that needs the meta data set.
     * @param fileName The String file name of the image that was used to create the texture.
     */
    /*public void setICFileNameMetaData(Texture tex, String fileName) {
        // add the filename metadata into the ImageComponent objects of the Texture
        if(tex!=null) {                       
            for(int imageIndex=0; imageIndex<tex.getImageCount(); imageIndex++) {
                ImageComponent ic = tex.getImage(imageIndex);
                if ((ic != null) && (fileName != null))
                    //ic.setUserData(XITH3D_USERDATAKEY_ICFILENAME, fileName);
                	ic.setUserData(fileName);
            }
        }
    }*/
	
    /**
     * Loads the image as fast as it can.  If it cannot load the
     * image then it returns null.
     */
    public synchronized BufferedImage loadImageFast(String name, boolean expectAlpha) {
        try {

            String cacheName = null;
            if (cachePath != null) {
                File f = new File(name);
                cacheName = cachePath+f.getName() + ".img";

                f = new File(cacheName);
                if (f.exists()) {
                    FileInputStream fs = new FileInputStream(f);
                    FileChannel fc = fs.getChannel();
                    MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY,0,f.length());
                    boolean hasAlpha = (bb.getInt()==1) ? true : false;
                    int width = bb.getInt();
                    int height = bb.getInt();
                    DirectBufferedImage im = null;
                    if (hasAlpha) im = (DirectBufferedImage)DirectBufferedImage.getDirectImageRGBA(width,height);
                    else im = (DirectBufferedImage)DirectBufferedImage.getDirectImageRGB(width,height);
                    bb.get(im.getBackingStore());
                    return im;
                }
            }

            BufferedImage im = null;
            try {
                // Loads image directly
                String filename = name;
                if (!(name.indexOf("\\")>0))
                    filename = findImageFile(name);
                    
                im = DirectBufferedImage.loadDirectImage(filename, expectAlpha);
            } catch (Exception e) {
                
                // Tries to load image from a Jar file
                URL imageUrl = findImageFileJar(name);
                im = DirectBufferedImage.loadDirectImage(imageUrl, expectAlpha);
            }
	    
	    
            if (cachePath != null) {
                DataOutputStream ds = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cacheName)));
                DirectBufferedImage dbi = (DirectBufferedImage)im;
                ds.writeInt(dbi.getColorModel().hasAlpha() ? 1 : 0);
                ds.writeInt(dbi.getWidth());
                ds.writeInt(dbi.getHeight());
                ds.write(dbi.getBackingStore());
                ds.close();
                ds = null;
            }
            return im;

        } catch (Exception notFound) {
        	System.out.println(notFound);
            throw new Error("Cannot load image " + name);
        }
    }
}
