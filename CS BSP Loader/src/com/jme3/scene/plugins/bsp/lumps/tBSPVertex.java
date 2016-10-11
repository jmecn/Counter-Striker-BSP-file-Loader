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

package com.jme3.scene.plugins.bsp.lumps;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;

/**
 * Insert package comments here
 * <p/>
 * Originally Coded by David Yazel on Jan 4, 2004 at 3:24:31 PM.
 */
public class tBSPVertex {

    public Vector3f position;
    public Vector2f texCoord;
    public Vector2f lightTexCoord;
    public Vector3f normal;
    public ColorRGBA color;

    public tBSPVertex() {
        position = new Vector3f();
        texCoord = new Vector2f();
        lightTexCoord = new Vector2f();
        normal = new Vector3f();
        color = new ColorRGBA();
    }

    public void swizzle() {
        float y= position.y;
        position.y = position.z;
        position.z = -y;
    }

    public void scale(float scale) {
        position.multLocal(scale);
        texCoord.multLocal(scale);
        lightTexCoord.multLocal(scale);
        normal.multLocal(scale);
        color.multLocal(scale);
    }

    public void add( tBSPVertex v) {
        position.addLocal(v.position);
        texCoord.addLocal(v.texCoord);
        lightTexCoord.addLocal(v.lightTexCoord);
        color.addLocal(v.color);
    }

    public void set(tBSPVertex a) {
        position.set(a.position);
        texCoord.set(a.texCoord);
        lightTexCoord.set(a.lightTexCoord);
        color.set(a.color);
    }
    public void avg(tBSPVertex a, tBSPVertex b) {
        set(a);
        add(b);
        scale(0.5f);
    }

    public tBSPVertex copy() {
        tBSPVertex v = new tBSPVertex();
        v.position.set(position);
        v.texCoord.set(texCoord);
        v.lightTexCoord.set(lightTexCoord);
        v.normal.set(normal);
        v.color.set(color);
        return v;
    }
}
