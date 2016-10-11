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

package trb.jme.quake3;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Hashtable;

import trb.jme.imaging.DirectBufferedImage;
import trb.jme.imaging.TextureLoader;
import trb.jme.quake3.lumps.tBSPFace;
import trb.jme.quake3.lumps.tBSPLeaf;
import trb.jme.quake3.lumps.tBSPVertex;

import com.jme3.texture.Texture;
import com.jme3.math.Vector3f;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Renderer;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.Mesh;

/**
 * Takes the data from a Quake3Loader and converts it into a Java3D scenegraph.  There is
 * obvisouly many ways to do this, so this can be thought of one way to render a quake map.
 * <p/>
 * Originally Coded by David Yazel on Jan 4, 2004 at 6:39:53 PM.
 * Ported to Java3D by Tom-Robert bryntesen on Dec 11, 2005
 */
public class Quake3Converter {

	/** All geometry is scaled by this value */
    final static float worldScale = 0.03f;

    /** The root of the Q3 level graph */
    private Node root;
    
    /** The lightmap textures */
    private Texture lightTextures[];
    
    /** The the base textures */
    private Texture textures[];
    
    /** Loads the Q3 file */
    private BSPLoader loader;
    
    /** Maps what cluster each leaf is part of */
    private int leafToCluster[];
    
    /** All the planes in the bsp tree described as xyzd */
    private float planes[];
    
    /** For node got the following information: planeIdx, frontNodeIdx and backNodeIdx stored in this array */
    private int nodes[];
    
    /** Holds the what faces is contained in each leaf */
    private BSPLeaf leafs[];
    
    /** All non null faces is represented in this switch with a Shape3D object */
    private BitSwitchNode faceSwitch;
    
    /** One bit for each face in faceSwitch */
    private BitSet faceBitset;
    private int bsLength = 0;
    
    /** An array of BSPLeafs for each cluster */
    private ArrayList[] clusterLeafs;
    
    /** The default texture is used if the real file is not found */
    private static Texture defTexture;
    
    /** Temp variable used by getCluster(...)*/
    private Vector3f normal = new Vector3f();
    
    /** The last visible cluster */
    private int lastCluster = -2;

    /** We cache unique appearances */
    private Hashtable uniqueAppearances = new Hashtable();
    
    /** The total number of triangles */
    private int triangleCnt = 0;
    
    /** Last value of usePVS */
    private boolean lastUsePVS = true;
    private AlphaState lightAlphaState;
    private HashMap vizMap = new HashMap();
    
    /**
     * Converts the information stored in the loader into a jME scenegraph
     */
    public void convert(BSPLoader loader, DisplaySystem display) {
        this.loader = loader;
        triangleCnt = 0;

        convertLightMaps();
        convertTextures();
        
        // add all the faces to faceSwitch and control wich are visible using faceBitset
        faceBitset = new BitSet(loader.faces.length);
        bsLength = loader.faces.length;
        faceSwitch = new BitSwitchNode("", faceBitset);
        setFaceBitset(true);
        for (int i = 0; i < loader.faces.length; i++) {
            Geometry face = convert(loader.faces[i], display);
            if (face != null) {
                faceSwitch.attachChild(face);
            } else {
            	faceSwitch.attachChild(new Node(""));
            }
        }

        // convert the leafs and set up the leaf to cluster mapping
        clusterLeafs = new ArrayList[loader.visData.numOfClusters];
        leafToCluster = new int[loader.leafs.length];
        leafs = new BSPLeaf[loader.leafs.length];
        for (int i = 0; i < loader.leafs.length; i++) {
            leafs[i] = convertLeaf(loader.leafs[i]);
            leafToCluster[i] = loader.leafs[i].cluster;
        }
        
        convertNodes();

        // root only contains the single faceSwitch
        root = new Node("root");
        root.attachChild(faceSwitch);
        
        System.out.println("Total number of triangles: "+triangleCnt);
        System.out.println("Total number of unique appearances: "+uniqueAppearances.size());
    }
    
    
    /**
     * Sets the value of all the elements of faceBitset.
     * @param value the value to set
     */
    private void setFaceBitset(boolean value) {
    	for (int i=0; i<bsLength; i++) {
    		faceBitset.set(i, value);
    	}
    }

    
    /**
	 * Builds image components for all the lightmaps.
	 */
    private void convertLightMaps() {
        lightTextures = new Texture[loader.lightmaps.length];
        for (int i = 0; i < loader.lightmaps.length; i++) {
            changeGamma(loader.lightmaps[i],1.2f);
            lightTextures[i] = TextureManager.loadTexture(
            		loader.lightmaps[i],
					Texture.MM_LINEAR_LINEAR,
					Texture.FM_LINEAR,
					false
					);
            lightTextures[i].setWrap(Texture.WM_WRAP_S_WRAP_T);
        }
    }

    
    /**
	 * Builds image components for all the lightmaps.
	 */
    private void convertTextures() {
		textures = new Texture[loader.textures.length];
        System.err.println("************** TEXTURES: "+textures.length);
		for (int i = 0; i < loader.textures.length; i++) {
			String t = loader.textures[i];
			t = t.substring(t.lastIndexOf("/") + 1);

			String fullpath = TextureLoader.getInstance().findImageFile(t + ".jpg");
			if (fullpath == null) {
				fullpath = TextureLoader.getInstance().findImageFile(t + ".tga");
				if (fullpath != null) {
					//BufferedImage bufferedImage = TargaFile.getBufferedImage(fullpath);
		            textures[i] = TextureManager.loadTexture(
		            		fullpath,
                            Texture.MM_LINEAR_LINEAR,
                            Texture.FM_LINEAR, 1.0f,
							false
							);
		            textures[i].setWrap(Texture.WM_CLAMP_S_CLAMP_T);
		            
				} else {
					textures[i] = getDefaultTexture();
				}
			} else {
	            textures[i] = TextureManager.loadTexture(
                        fullpath,
                        Texture.MM_LINEAR_LINEAR,
                        Texture.FM_LINEAR, 1.0f,
						false
						);
	            textures[i].setWrap(Texture.WM_WRAP_S_WRAP_T);
			}
		}
	}

    
    /**
     * Gets a reference to the default texture
     */
    static private Texture getDefaultTexture() {
        if (defTexture != null) {
        	return defTexture;
        }
        
        BufferedImage im = DirectBufferedImage.getDirectImageRGB(256, 256);
        Graphics g = im.getGraphics();
        g.setColor(Color.gray);
        g.fillRect(0, 0, 256, 256);
        g.dispose();

        defTexture = TextureManager.loadTexture(
        		im,
				Texture.MM_LINEAR,
				Texture.FM_LINEAR,
				false
				);
        return defTexture;
    }

    
    /**
     * This function was taken from a couple engines that I saw,
     * which most likely originated from the Aftershock engine.
     * Kudos to them!  What it does is increase/decrease the intensity
     * of the lightmap so that it isn't so dark.  Quake uses hardware to
     * do this, but we will do it in code. 
     * @param im the image to change
     * @param factor how much the image is gamma corrected
     */
    public static void changeGamma(DirectBufferedImage im, float factor) {
        byte pImage[] = im.getBackingStore();
		int psize = (im.getDirectType() == DirectBufferedImage.DIRECT_RGBA) ? 4	: 3;

		byte gtable[] = new byte[256];
		for (int i = 0; i < 256; i++) {
			gtable[i] = (byte) Math.floor(255.0 * Math.pow(i / 255.0, 1.0 / factor) + 0.5);
		}

		// Go through every pixel in the lightmap
		final int size = im.getWidth() * im.getHeight();
		for (int i = 0; i < size; i++) {
			pImage[i * psize + 0] = gtable[pImage[i * psize + 0] & 0xff];
			pImage[i * psize + 1] = gtable[pImage[i * psize + 1] & 0xff];
			pImage[i * psize + 2] = gtable[pImage[i * psize + 2] & 0xff];
		}
    }

    
    /**
	 * Takes all the faces in the leaf and adds them to the cluster
	 */
    private BSPLeaf convertLeaf(tBSPLeaf leaf) {
		if (leaf.numOfLeafFaces == 0) {
			return new BSPLeaf();
		}

		BSPLeaf l = new BSPLeaf();
		l.faces = new int[leaf.numOfLeafFaces];
		for (int i = 0; i < leaf.numOfLeafFaces; i++) {
			l.faces[i] = loader.leafFaces[i + leaf.leafface];
		}
		if (clusterLeafs[leaf.cluster] == null) {
			clusterLeafs[leaf.cluster] = new ArrayList(20);
		}
		clusterLeafs[leaf.cluster].add(l);

		return l;
	}

    int count = 0;
    /**
     * Converts a BSP face definition into a Shape3D
     */
    private Geometry convert(tBSPFace face, DisplaySystem display) {
    	Geometry triMesh = null;
        switch (face.type) {

        case 1:
        case 3:
        	// either polygon or model
            triMesh = convertToIndexed(face);
            break;
        case 2:
        	// bezier patch
            triMesh = convertSurfacePatch(face);
            break;
        default:
            return null;
        }
        triMesh.setName("tm: "+count);
        count++;
        
        if (face.textureID<0) {
            System.out.println("no texture, skipping");
            return null;
        }
        
        triMesh.setModelBound(new com.jme3.bounding.BoundingBox()); 
        triMesh.updateModelBound();
        
        TextureState textureState = display.getRenderer().createTextureState();
        textureState.setTexture(textures[face.textureID], 0);
        triMesh.setRenderState(textureState);
		if (face.lightmapID < 0) {
            textures[face.textureID].setApply(Texture.AM_REPLACE);
            if (loader.textures[face.textureID].indexOf("flame1side") != -1 ||
                    loader.textures[face.textureID].indexOf("flame1dark") != -1) {
                Texture tex = textures[face.textureID];
                tex.setApply(Texture.AM_MODULATE);
                if (lightAlphaState == null) {
                    lightAlphaState = display.getRenderer().createAlphaState();
                    lightAlphaState.setBlendEnabled(true);
                    lightAlphaState.setSrcFunction(AlphaState.SB_ONE);
                    lightAlphaState.setDstFunction(AlphaState.DB_ONE);
                    lightAlphaState.setTestEnabled(true);
                    lightAlphaState.setTestFunction(AlphaState.TF_GREATER);
                }
                triMesh.setRenderQueueMode(Renderer.QUEUE_TRANSPARENT);
                triMesh.setRenderState(lightAlphaState);
            }
        } else {
            Texture lt = lightTextures[face.lightmapID];
            lt.setApply(Texture.AM_COMBINE);
            lt.setCombineFuncRGB(Texture.ACF_MODULATE);
            lt.setCombineSrc0RGB(Texture.ACS_PREVIOUS);
            lt.setCombineOp0RGB(Texture.ACO_SRC_COLOR);
            lt.setCombineSrc1RGB(Texture.ACS_TEXTURE);
            lt.setCombineOp1RGB(Texture.ACO_SRC_COLOR);
            lt.setCombineScaleRGB(2);
            textureState.setTexture(lightTextures[face.lightmapID], 1);
        }
        
        return triMesh;
    }
    

    /**
     * Creates the indexed geometry array for the BSP face.  The lightmap tex coords are stored in
     * unit 1, the regular tex coords are stored in unit 2
     * @param face the bsp face to convert 
     * @return a IndexTriangleArray containing the geometry of the face
     */
    private Geometry convertToIndexed(tBSPFace face) {
    	triangleCnt += face.numMeshVerts/3;
    	
    	FloatBuffer coordinates = BufferUtils.createFloatBuffer(face.numOfVerts*3);
    	FloatBuffer texCoords = BufferUtils.createFloatBuffer(face.numOfVerts*2);
    	FloatBuffer lightTexCoords = BufferUtils.createFloatBuffer(face.numOfVerts*2);
        for (int i = 0; i < face.numOfVerts; i++) {
            int j = face.vertexIndex + i;
        	coordinates.put(loader.vertices[j].position.x * worldScale);
        	coordinates.put(loader.vertices[j].position.z * worldScale);
        	coordinates.put(-loader.vertices[j].position.y * worldScale);
        	texCoords.put(loader.vertices[j].texCoord.x);
        	texCoords.put(loader.vertices[j].texCoord.y);
        	lightTexCoords.put(loader.vertices[j].lightTexCoord.x);
        	lightTexCoords.put(loader.vertices[j].lightTexCoord.y);
        }
        coordinates.rewind();
        texCoords.rewind();
        lightTexCoords.rewind();
    	
        int index[] = new int[face.numMeshVerts];
        System.arraycopy(loader.meshVertices, face.meshVertIndex, index, 0, face.numMeshVerts);
        IntBuffer indices = BufferUtils.createIntBuffer(index.length);
        indices.put(index);
        indices.rewind();
    	TriMesh triMesh = new TriMesh("", coordinates, null, null, texCoords, indices);
        triMesh.setCullMode(Spatial.CULL_DYNAMIC);
        triMesh.setRenderQueueMode(Renderer.QUEUE_OPAQUE);
    	triMesh.setTextureBuffer(lightTexCoords, 1);
    	return triMesh;
    }
    

    /**
     * Creates the indexed geometry array for the BSP curved surface face.  The
     * lightmap tex coords are stored in unit 1, the regular tex coords are stored in unit 2
     * @param face the bsp face to convert 
     * @return a IndexTriangleArray containing the geometry of the face
     */
    private Geometry convertSurfacePatch(tBSPFace face) {
        tBSPVertex control[] = new tBSPVertex[face.numOfVerts];
        for (int i=0;i<face.numOfVerts;i++)
            control[i] = loader.vertices[face.vertexIndex+i];
        PatchSurface ps = new PatchSurface(control,face.numOfVerts,face.size[0],face.size[1]);

    	FloatBuffer coordinates = BufferUtils.createFloatBuffer(ps.mPoints.length*3);
    	FloatBuffer texCoords = BufferUtils.createFloatBuffer(ps.mPoints.length*2);
    	FloatBuffer lightTexCoords = BufferUtils.createFloatBuffer(ps.mPoints.length*2);
        for (int i = 0; i < ps.mPoints.length; i++) {
            coordinates.put(ps.mPoints[i].position.x * worldScale);
        	coordinates.put(ps.mPoints[i].position.z * worldScale);
        	coordinates.put(-ps.mPoints[i].position.y * worldScale);
        	texCoords.put(ps.mPoints[i].texCoord.x);
        	texCoords.put(ps.mPoints[i].texCoord.y);
        	lightTexCoords.put(ps.mPoints[i].lightTexCoord.x);
        	lightTexCoords.put(ps.mPoints[i].lightTexCoord.y);
        }
        coordinates.rewind();
        texCoords.rewind();
        lightTexCoords.rewind();
    	
        IntBuffer indices = BufferUtils.createIntBuffer(ps.mIndices.length);
        indices.put(ps.mIndices);
        indices.rewind();
    	
    	TriMesh triMesh = new TriMesh("", coordinates, null, null, texCoords, indices);
        triMesh.setCullMode(Spatial.CULL_DYNAMIC);
        triMesh.setRenderQueueMode(Renderer.QUEUE_OPAQUE);
    	triMesh.setTextureBuffer(lightTexCoords, 1);
    	return triMesh;
    }

    
    /**
	 * converts the nodes and planes for the BSP
	 */
    private void convertNodes() {
		nodes = new int[loader.nodes.length * 3];
		for (int i = 0; i < loader.nodes.length; i++) {
			final int j = i * 3;
			nodes[j + 0] = loader.nodes[i].plane;
			nodes[j + 1] = loader.nodes[i].front;
			nodes[j + 2] = loader.nodes[i].back;
		}

		// now convert the planes
		planes = new float[loader.planes.length * 4];
		for (int i = 0; i < loader.planes.length; i++) {
			final int j = i * 4;
			planes[j + 0] = loader.planes[i].normal.x;
			planes[j + 1] = loader.planes[i].normal.z;
			planes[j + 2] = -loader.planes[i].normal.y;
			planes[j + 3] = loader.planes[i].d * worldScale;
		}
	}


    /**
     * Disables geometry that is invisible according to the PVS
     * @param camPos the position of the camera
     * @param usePVS if true the PVS is used to disable hidden geometry, false and everyting is enabled
     */
    public void setVisibility(Vector3f camPos, boolean usePVS) {
    	boolean usePVSChanged = usePVS != lastUsePVS;
    	lastUsePVS = usePVS;
    	
    	if (!usePVS) {
    		//faceBitset.set(0, faceBitset.size() - 1);
    		if (usePVSChanged) {
    			setFaceBitset(true);
    		}
    		return;
    	}
		
    	int camCluster = getCluster(camPos);
		if (lastCluster == camCluster && !usePVSChanged) {
			return;
		}

		System.out.println("new cluster is " + camCluster);
		lastCluster = camCluster;

		setFaceBitset(false);
		int numVis = 0;
		int numFacesVis = 0;
		int numClusters = 0;
		int numLeaves = 0;
        vizMap.clear();
		for (int i = 0; i < loader.visData.numOfClusters; i++) {
			boolean isVisible = isClusterVisible(camCluster, i);
			if (clusterLeafs[i] != null) {
				numClusters++;
				
			}
			if (isVisible && clusterLeafs[i] != null) {
				boolean hasFaces = false;
				for (int j = 0, clSize = clusterLeafs[i].size(); j < clSize; j++) {
					BSPLeaf l = (BSPLeaf)clusterLeafs[i].get(j);
					if (l.faces != null && l.faces.length > 0) {
						numLeaves++;
					}
					for (int k = 0; k < l.faces.length; k++) {
						if (!faceBitset.get(l.faces[k])) {
							faceBitset.set(l.faces[k]);
							Long key = new Long(i << 24
									| loader.faces[l.faces[k]].textureID << 8
									| loader.faces[l.faces[k]].lightmapID);
							if (vizMap.get(key) == null) {
                                vizMap.put(key, key);
							}
							numFacesVis++;
							hasFaces = true;
						}
					}
				}
				if (hasFaces) {
					numVis++;
				}
			}
		}
		//faceSwitch.setChildMask(faceBitset);
		/*
		 System.out.println("num cluster is visible is "+numVis+" out of "+numClusters);
		 System.out.println("num leaves visible is "+numLeaves+" out of "+loader.leafs.length);
		 System.out.println("num faces visible is "+numFacesVis+" out of "+faces.length);
		 System.out.println("num unique shapes is "+map.size());
		 */
	}


    /**
	 * Calculates which cluster the camera position is in.
	 * @param pos the coordinate to check
	 * @return the cluster containing the specified position.
	 */
    private int getCluster( Vector3f pos ) {
        int index = 0;
        while (index >= 0) {
            final int node = index * 3;
            final int planeIndex = nodes[node+0]*4;
            normal.x = planes[planeIndex+0];
            normal.y = planes[planeIndex+1];
            normal.z = planes[planeIndex+2];
            float d = planes[planeIndex+3];

            // Distance from point to a plane
            final float distance = normal.dot(pos) - d;

            if (distance > 0.0001) {
                index = nodes[node+1];
            } else {
                index = nodes[node+2];
            }
        }

        return leafToCluster[-(index + 1)];
    }


    /**
     * Checks if testCluster is visible from visCluster.
     * @param visCluster the position of the viewer
     * @param testCluster the clustor to check if is visible from visCluster
     * @return true if cluster is visible
     */
    private boolean isClusterVisible(int visCluster, int testCluster) {
        if ((loader.visData.pBitsets == null) || (visCluster < 0)) {
            return true;
        }

        int i = (visCluster * loader.visData.bytesPerCluster) + (testCluster /8);
        return (loader.visData.pBitsets[i] & (1 << (testCluster & 0x07))) != 0;
    }

    
    /**
     * Gets the root of the graph containing the level
     */
    public Node getRoot() {
        return root;
    }
    

    /**
     * Helper class and contains a list of faces.
     */
    public class BSPLeaf {
    	/** Faces contained by this leaf */
        int faces[];
    }
}