package com.jme3.scene.plugins.bsp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import org.apache.log4j.Logger;

import trb.jme.imaging.DirectBufferedImage;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.asset.plugins.ClasspathLocator;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.plugins.bsp.lumps.tBSPFace;
import com.jme3.scene.plugins.bsp.lumps.tBSPLeaf;
import com.jme3.scene.plugins.bsp.lumps.tBSPLump;
import com.jme3.scene.plugins.bsp.lumps.tBSPModel;
import com.jme3.scene.plugins.bsp.lumps.tBSPNode;
import com.jme3.scene.plugins.bsp.lumps.tBSPPlane;
import com.jme3.scene.plugins.bsp.lumps.tBSPVertex;
import com.jme3.scene.plugins.bsp.lumps.tBSPVisData;

public class BSPLoader implements AssetLoader {
	
	public static void main(String[] args) {
		AssetManager assetManager = new DesktopAssetManager();
		assetManager.registerLocator("/", ClasspathLocator.class);
		assetManager.registerLoader(BSPLoader.class, "bsp");
		assetManager.loadModel("cstrike/maps/de_dust2.bsp");
	}

	static Logger logger = Logger.getLogger(BSPLoader.class);

	public final static int kEntities = 0; // Stores player/object positions, etc...
	public final static int kPlanes = 1; // Stores the splitting planes
	public final static int kTextures = 2; // Stores texture information
	public final static int kVertices = 3; // Stores the level vertices
	public final static int kVisData = 4; // Stores PVS and cluster info (visibility)
	public final static int kNodes = 5; // Stores the BSP nodes
	public final static int kFaces = 7; // Stores the faces for the level
	public final static int kLightVolumes = 8; // Stores extra world lighting
												// information
	public final static int kLeafs = 10; // Stores the leafs of the nodes
	public final static int kLeafFaces = 5; // Stores the leaf's indices into
											// the faces
	public final static int kLeafBrushes = 6; // Stores the leaf's indices into
												// the brushes
	public final static int kModels = 14; // Stores the info of world models
	public final static int kBrushes = 8; // Stores the brushes info (for
											// collision)
	public final static int kBrushSides = 9; // Stores the brush surfaces info
	public final static int kMeshVerts = 11; // Stores the model vertices
												// offsets
	public final static int kShaders = 12; // Stores the shader files (blending,
											// anims..)
	public final static int kLightmaps = 14; // Stores the lightmaps for the
												// level
	public final static int kMaxLumps = 17; // A constant to store the number of
											// lumps

	private tBSPLump lumps[];
	private tBSPPlane[] planes;
	private tBSPFace[] faces;
	private tBSPVertex[] vertices;
	private tBSPVisData visData;
	private tBSPLeaf[] leafs;
	private tBSPNode[] nodes;
	private tBSPModel[] models;
	private String[] textures;
	private int leafFaces[];
	private int meshVertices[];

	DirectBufferedImage lightmaps[];

	ByteBuffer buffer = null;

	@Override
	public Object load(AssetInfo assetInfo) throws IOException {
		// 开始读取数据
		InputStream inputStream = assetInfo.openStream();
		int totalAvailable = inputStream.available();
		ReadableByteChannel channel = Channels.newChannel(inputStream);

		// 读取文件头，检查是否是counter-striker的BSP文件
		int headerLength = 124;// 文件头的长度
		ByteBuffer header = ByteBuffer.allocate(headerLength);
		header.order(ByteOrder.LITTLE_ENDIAN);
		channel.read(header);
		header.flip();

		// 检查文件版本
		int version = header.getInt();
		if (version != 0x1e)
			throw new IOException("Invalid counter-striker BSP file");

		// 读取Lump
		lumps = new tBSPLump[15];
		for (int i = 0; i < 15; i++) {
			lumps[i] = new tBSPLump();
			lumps[i].offset = header.getInt();
			lumps[i].length = header.getInt();
		}

		// 计算文件长度
		buffer = ByteBuffer.allocate(totalAvailable);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.put(header);
		buffer.position(headerLength);
		channel.read(buffer);

		// Ready
		buffer.flip();

		logger.info("buffer : " + buffer);
		readEntities();
		readPlanes();
		readTextures();
		readVertices();
		readLightmaps();
		readLeafs();
		readLeafFaces();
		readMeshVertices();
		readNodes();
		readModels();
		readVisData();
		readFaces();

		// Build scene
		Node rootNode = new Node("bsp model");

		return rootNode;
	}

	private void readEntities() throws IOException {
		buffer.position(lumps[kEntities].offset);
		int num = lumps[kEntities].length;
		logger.info(lumps[kEntities] + " entities");
		byte[] ca = new byte[num];

		readFully(ca);
		String s = new String(ca);
		// System.out.println(s);
	}
	
	private void readPlanes() throws IOException {

		buffer.position(lumps[kPlanes].offset);
		int num = lumps[kPlanes].length / (4 * 4);
		
		logger.info(lumps[kPlanes] + " there are " + num + " planes");

		planes = new tBSPPlane[num];
		for (int i = 0; i < num; i++) {
			planes[i] = new tBSPPlane();
			planes[i].normal.x = readFloat();
			planes[i].normal.y = readFloat();
			planes[i].normal.z = readFloat();
			planes[i].d = readFloat();
		}

	}

	private void readVisData() throws IOException {
		buffer.position(lumps[kVisData].offset);
		visData = new tBSPVisData();
		visData.numOfClusters = readInt();
		visData.bytesPerCluster = readInt();
		logger.info("There are " + visData.numOfClusters + " clusters with "
				+ visData.bytesPerCluster + " bytes of vis data each");
		visData.pBitsets = new byte[visData.bytesPerCluster
				* visData.numOfClusters];
		readFully(visData.pBitsets);
	}

	private void readNodes() throws IOException {

		buffer.position(lumps[kNodes].offset);
		int num = lumps[kNodes].length / (4 * 9);
		nodes = new tBSPNode[num];
		for (int i = 0; i < num; i++) {
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

		buffer.position(lumps[kModels].offset);
		int num = lumps[kModels].length / (4 * 10);
		models = new tBSPModel[num];
		for (int i = 0; i < num; i++) {

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

	private void readLightmaps() throws IOException {

		buffer.position(lumps[kLightmaps].offset);
		int num = lumps[kLightmaps].length / (128 * 128 * 3);
		logger.info("there are " + num + " lightmaps");

		lightmaps = new DirectBufferedImage[num];
		for (int i = 0; i < num; i++) {
			lightmaps[i] = DirectBufferedImage.getDirectImageRGB(128, 128);
			readFully(lightmaps[i].getBackingStore());
		}
	}

	private void readTextures() throws IOException {

		buffer.position(lumps[kTextures].offset);
		int num = lumps[kTextures].length / (64 + 2 * 4);
		logger.info(lumps[kTextures] + "there are " + num + " textures");

		byte[] ca = new byte[64];
		textures = new String[num];
		for (int i = 0; i < num; i++) {
			readFully(ca);
			readInt();
			readInt();
			String s = new String(ca);
			s = s.substring(0, s.indexOf(0));
			textures[i] = s;
		}
	}

	private void readLeafs() throws IOException {

		buffer.position(lumps[kLeafs].offset);
		int num = lumps[kLeafs].length / (12 * 4);
		logger.info("there are " + num + " leafs");

		leafs = new tBSPLeaf[num];
		for (int i = 0; i < num; i++) {
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

		buffer.position(lumps[kLeafFaces].offset);
		int num = lumps[kLeafFaces].length / 4;
		logger.info("there are " + num + " leaf faces");

		leafFaces = new int[num];
		for (int i = 0; i < num; i++) {
			leafFaces[i] = readInt();
		}

	}

	private void readMeshVertices() throws IOException {

		buffer.position(lumps[kMeshVerts].offset);
		int num = lumps[kMeshVerts].length / 4;
		logger.info("there are " + num + " mesh vertices");

		meshVertices = new int[num];
		for (int i = 0; i < num; i++) {
			meshVertices[i] = readInt();
		}

	}

	private void readVertices() throws IOException {

		buffer.position(lumps[kVertices].offset);
		int num = lumps[kVertices].length / (11 * 4);
		logger.info("there are " + num + " vertices");

		vertices = new tBSPVertex[num];
		for (int i = 0; i < num; i++) {

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

			int r = buffer.get();
			if (r < 0)
				r = -r + 127;

			int g = buffer.get();
			if (g < 0)
				g = -g + 127;

			int b = buffer.get();
			if (b < 0)
				b = -b + 127;

			int a = buffer.get();
			if (a < 0)
				a = -a + 127;

			vertices[i].color.r = (float) (r) / 255f;
			vertices[i].color.g = (float) (g) / 255f;
			vertices[i].color.b = (float) (b) / 255f;
			vertices[i].color.a = (float) (a) / 255f;
		}

	}

	private void readFaces() throws IOException {

		buffer.position(lumps[kFaces].offset);
		int num = lumps[kFaces].length / (26 * 4);
		logger.info("there are " + num + " faces");

		faces = new tBSPFace[num];
		for (int i = 0; i < num; i++) {
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
		}

	}

	public int readInt() {
		return buffer.getInt();
	}

	public float readFloat() throws IOException {
		return buffer.getFloat();
	}

	/**
	 * Reads 3 floats x,z,y from the chunkbuffer. Since 3ds has z as up and y as
	 * pointing in whereas java3d has z as pointing forward and y as pointing
	 * up; this returns new Point3f(x,-z,y)
	 */
	public Vector3f getVector3f() {
		float x = buffer.getFloat();
		float z = -buffer.getFloat();
		float y = buffer.getFloat();

		return new Vector3f(x, y, z);
	}

	/**
	 * This reads bytes until it gets 0x00 and returns the corresponding string.
	 */
	public String getString() {
		StringBuffer stringBuffer = new StringBuffer();
		char charIn = (char) buffer.get();
		while (charIn != 0x00) {
			stringBuffer.append(charIn);
			charIn = (char) buffer.get();
		}
		return stringBuffer.toString();
	}

	public void readFully(byte[] data) {
		buffer.get(data);
	}
}
