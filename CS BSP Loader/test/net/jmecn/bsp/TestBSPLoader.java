package net.jmecn.bsp;

import com.jme3.app.SimpleApplication;
import com.jme3.scene.Spatial;
import com.jme3.scene.plugins.bsp.BSPLoader;

public class TestBSPLoader extends SimpleApplication {

	@Override
	public void simpleInitApp() {
		assetManager.registerLoader(BSPLoader.class, "bsp");
		
		Spatial model = assetManager.loadModel("cstrike/maps/de_dust2.bsp");
		rootNode.attachChild(model);
	}

	public static void main(String[] args) {

		TestBSPLoader app = new TestBSPLoader();
		app.start();
	}

}
