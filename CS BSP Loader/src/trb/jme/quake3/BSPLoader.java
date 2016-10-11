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


import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import trb.jme.imaging.DirectBufferedImage;
import trb.jme.quake3.lumps.tBSPDirectory;
import trb.jme.quake3.lumps.tBSPFace;
import trb.jme.quake3.lumps.tBSPLeaf;
import trb.jme.quake3.lumps.tBSPLump;
import trb.jme.quake3.lumps.tBSPModel;
import trb.jme.quake3.lumps.tBSPNode;
import trb.jme.quake3.lumps.tBSPPlane;
import trb.jme.quake3.lumps.tBSPVertex;
import trb.jme.quake3.lumps.tBSPVisData;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;


/**
 * Loads the quake 3 BSP file according to spec.  It is not expected that it would be
 * rendered from this data structure, but used to convert into a better format for
 * rendering via xith3d
 * <p/>
 * Originally Coded by David Yazel on Jan 4, 2004 at 10:50:22 AM.
 */
public class BSPLoader implements AssetLoader {

    tBSPDirectory directory;
    tBSPFace[] faces;
    tBSPVertex[] vertices;
    tBSPVisData visData;
    tBSPLeaf[] leafs;
    tBSPPlane[] planes;
    tBSPNode[] nodes;
    tBSPModel[] models;
    String[] textures;
    int leafFaces[];
    int meshVertices[];

    DirectBufferedImage lightmaps[];

    byte[] byteBuffer = null;
    FloatBuffer floatBuffer = null;
    ByteBuffer bBuffer = null;
    IntBuffer intBuffer = null;
    ShortBuffer shortBuffer = null;
    RandomAccessFile in;

    public BSPLoader() {
        byteBuffer = new byte[4];
        bBuffer = ByteBuffer.wrap(byteBuffer);
        bBuffer.order(ByteOrder.LITTLE_ENDIAN);
        floatBuffer = bBuffer.asFloatBuffer();
        intBuffer = bBuffer.asIntBuffer();
    }


    public int readInt() throws IOException {
        in.read(byteBuffer);
        return intBuffer.get(0);
    }

    public float readFloat() throws IOException {
        in.read(byteBuffer);
        return floatBuffer.get(0);
    }

    private void readDirectory() throws IOException {
        directory = new tBSPDirectory();
        for (int i=0;i<17;i++) {
            directory.directory[i] = new tBSPLump();
            directory.directory[i].offset = readInt();
            directory.directory[i].length = readInt();
            //System.out.println("lump "+i+": offset="+directory.directory[i].offset+", length="+directory.directory[i].length );
        }
    }


    private void readVisData() throws IOException {
        in.seek(directory.directory[tBSPDirectory.kVisData].offset);
        visData = new tBSPVisData();
        visData.numOfClusters = readInt();
        visData.bytesPerCluster = readInt();
        System.out.println("There are "+visData.numOfClusters+" clusters with "+visData.bytesPerCluster+" bytes of vis data each");
        visData.pBitsets = new byte[visData.bytesPerCluster * visData.numOfClusters];
        in.readFully(visData.pBitsets);
    }

    private void readPlanes() throws IOException {

        in.seek(directory.directory[tBSPDirectory.kPlanes].offset);
        int num = directory.directory[tBSPDirectory.kPlanes].length/(4*4);
        planes = new tBSPPlane[num];
        for (int i=0;i<num;i++) {
            planes[i] = new tBSPPlane();
            planes[i].normal.x = readFloat();
            planes[i].normal.y = readFloat();
            planes[i].normal.z = readFloat();
            planes[i].d = readFloat();
        }

    }

    private void readNodes() throws IOException {

        in.seek(directory.directory[tBSPDirectory.kNodes].offset);
        int num = directory.directory[tBSPDirectory.kNodes].length/(4*9);
        nodes = new tBSPNode[num];
        for (int i=0;i<num;i++) {
            nodes[i] = new tBSPNode();
            nodes[i].plane = readInt();
            nodes[i].front = readInt();
            nodes[i].back = readInt();

            nodes[i].mins[0] = readInt();
            nodes[i].mins[1] = readInt();
            nodes[i].mins[2] = readInt();

            nodes[i].maxs[0] = readInt();
            nodes[i].maxs[1] = readInt();
            nodes[i].maxs[2] = readInt();

        }

    }

    private void readModels() throws IOException {

        in.seek(directory.directory[tBSPDirectory.kModels].offset);
        int num = directory.directory[tBSPDirectory.kModels].length/(4*10);
        models = new tBSPModel[num];
        for (int i=0;i<num;i++) {

            models[i] = new tBSPModel();

            models[i].min[0] = readFloat();
            models[i].min[1] = readFloat();
            models[i].min[2] = readFloat();

            models[i].max[0] = readFloat();
            models[i].max[1] = readFloat();
            models[i].max[2] = readFloat();

            models[i].faceIndex = readInt();
            models[i].numOfFaces = readInt();
            models[i].brushIndex = readInt();
            models[i].numOfBrushes = readInt();

        }

    }

    private void readEntities() throws IOException {
    	/*
        in.seek(directory.directory[tBSPDirectory.kEntities].offset);
        int num = directory.directory[tBSPDirectory.kEntities].length;
        byte[] ca = new byte[num];

        in.readFully(ca);
        String s = new String(ca);
        System.out.println(s);
        */
    }

    private void readLightmaps() throws IOException {

        in.seek(directory.directory[tBSPDirectory.kLightmaps].offset);
        int num = directory.directory[tBSPDirectory.kLightmaps].length / (128*128*3);
        System.out.println("there are "+num+" lightmaps");

        lightmaps = new DirectBufferedImage[num];
        for (int i=0;i<num;i++) {
            lightmaps[i] = DirectBufferedImage.getDirectImageRGB(128,128);
            in.readFully(lightmaps[i].getBackingStore());
        }
    }

    private void readTextures() throws IOException {

        in.seek(directory.directory[tBSPDirectory.kTextures].offset);
        int num = directory.directory[tBSPDirectory.kTextures].length / (64+2*4);
        System.out.println("there are "+num+" textures");

        byte[] ca = new byte[64];
        textures = new String[num];
        for (int i=0;i<num;i++) {
            in.readFully(ca);
            readInt();
            readInt();
            String s = new String(ca);
            s = s.substring(0,s.indexOf(0));
            textures[i] = s;
        }
    }

    private void readLeafs() throws IOException {

        in.seek(directory.directory[tBSPDirectory.kLeafs].offset);
        int num = directory.directory[tBSPDirectory.kLeafs].length / (12*4);
        System.out.println("there are "+num+" leafs");

        leafs = new tBSPLeaf[num];
        for (int i=0;i<num;i++) {
            leafs[i] = new tBSPLeaf();
            leafs[i].cluster = readInt();
            leafs[i].area = readInt();

            leafs[i].mins[0] = readInt();
            leafs[i].mins[1] = readInt();
            leafs[i].mins[2] = readInt();

            leafs[i].maxs[0] = readInt();
            leafs[i].maxs[1] = readInt();
            leafs[i].maxs[2] = readInt();

            leafs[i].leafface = readInt();
            leafs[i].numOfLeafFaces = readInt();

            leafs[i].leafBrush = readInt();
            leafs[i].numOfLeafBrushes = readInt();

        }
    }

    private void readLeafFaces() throws IOException {

        in.seek(directory.directory[tBSPDirectory.kLeafFaces].offset);
        int num = directory.directory[tBSPDirectory.kLeafFaces].length / 4;
        System.out.println("there are "+num+" leaf faces");

        leafFaces = new int[num];
        for (int i=0;i<num;i++) {
            leafFaces[i] = readInt();
        }

    }

    private void readMeshVertices() throws IOException {

        in.seek(directory.directory[tBSPDirectory.kMeshVerts].offset);
        int num = directory.directory[tBSPDirectory.kMeshVerts].length / 4;
        System.out.println("there are "+num+" mesh vertices");

        meshVertices = new int[num];
        for (int i=0;i<num;i++) {
            meshVertices[i] = readInt();
        }

    }

    private void readVertices() throws IOException {

        in.seek(directory.directory[tBSPDirectory.kVertices].offset);
        int num = directory.directory[tBSPDirectory.kVertices].length / (11*4);
        System.out.println("there are "+num+" vertices");

        vertices = new tBSPVertex[num];
        for (int i=0;i<num;i++) {

            vertices[i] = new tBSPVertex();
            vertices[i].position.x = readFloat();
            vertices[i].position.y = readFloat();
            vertices[i].position.z = readFloat();

            vertices[i].texCoord.x = readFloat();
            vertices[i].texCoord.y = readFloat();

            vertices[i].lightTexCoord.x = readFloat();
            vertices[i].lightTexCoord.y = readFloat();

            vertices[i].normal.x = readFloat();
            vertices[i].normal.y = readFloat();
            vertices[i].normal.z = readFloat();

            int r = in.readByte();
            if (r<0) r = -r+127;

            int g = in.readByte();
            if (g<0) g = -g+127;

            int b = in.readByte();
            if (b<0) b = -b+127;

            int a = in.readByte();
            if (a<0) a = -a+127;

            vertices[i].color.r = (float)(r)/255f;
            vertices[i].color.g = (float)(g)/255f;
            vertices[i].color.b = (float)(b)/255f;
            vertices[i].color.a = (float)(a)/255f;
        }

    }
    private void readFaces() throws IOException {

        in.seek(directory.directory[tBSPDirectory.kFaces].offset);
        int num = directory.directory[tBSPDirectory.kFaces].length / (26*4);
        System.out.println("there are "+num+" faces");

        faces = new tBSPFace[num];
        for (int i=0;i<num;i++) {
            faces[i] = new tBSPFace();
            faces[i].textureID = readInt();
            faces[i].effect = readInt();
            faces[i].type = readInt();
            faces[i].vertexIndex = readInt();
            faces[i].numOfVerts = readInt();
            faces[i].meshVertIndex = readInt();
            faces[i].numMeshVerts = readInt();
            faces[i].lightmapID = readInt();
            faces[i].lMapCorner[0] = readInt();
            faces[i].lMapCorner[1] = readInt();

            faces[i].lMapSize[0] = readInt();
            faces[i].lMapSize[1] = readInt();

            faces[i].lMapPos[0] = readFloat();
            faces[i].lMapPos[1] = readFloat();
            faces[i].lMapPos[2] = readFloat();

            faces[i].lMapBitsets[0][0] = readFloat();
            faces[i].lMapBitsets[0][1] = readFloat();
            faces[i].lMapBitsets[0][2] = readFloat();

            faces[i].lMapBitsets[1][0] = readFloat();
            faces[i].lMapBitsets[1][1] = readFloat();
            faces[i].lMapBitsets[1][2] = readFloat();

            faces[i].vNormal[0] = readFloat();
            faces[i].vNormal[1] = readFloat();
            faces[i].vNormal[2] = readFloat();

            faces[i].size[0] = readInt();
            faces[i].size[1] = readInt();
//            System.out.println("type="+faces[i].type+", verts "+faces[i].numOfVerts+", "+faces[i].numMeshVerts);

        }

    }
    

	@Override
	public Object load(AssetInfo assetInfo) throws IOException {
		AssetManager manager = assetInfo.getManager();
		InputStream input = assetInfo.openStream();
		// TODO Auto-generated method stub
		return null;
	}
	
    public void load(String filename) throws IOException {

        in = new RandomAccessFile(filename,"r");
        char strID[] = new char[4];
        strID[0] = (char) in.readByte();
        strID[1] = (char) in.readByte();
        strID[2] = (char) in.readByte();
        strID[3] = (char) in.readByte();

        int version = readInt();
        if (version != 0x2e) throw new IOException("Invalid qake 3 BSP file");

        readDirectory();
        readFaces();
        readVertices();
        readLightmaps();
        readVisData();
        readLeafs();
        readTextures();
        readLeafFaces();
        readMeshVertices();
        readEntities();
        readPlanes();
        readNodes();
        readModels();

        in.close();

    }

}
