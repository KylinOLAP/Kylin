/*
 * Copyright 2013-2014 eBay Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kylinolap.cube.cuboid;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.kylinolap.common.util.LocalFileMetadataTestCase;
import com.kylinolap.cube.CubeDescManager;
import com.kylinolap.cube.model.CubeDesc;
import com.kylinolap.metadata.MetadataManager;

/**
 * @author yangli9
 */
public class CuboidTest extends LocalFileMetadataTestCase {

    private long toLong(String bin) {
        return Long.parseLong(bin, 2);
    }

    public CubeDescManager getCubeDescManager() {
        return CubeDescManager.getInstance(getTestConfig());
    }

    private CubeDesc getTestKylinCubeII() {
        return getCubeDescManager().getCubeDesc("test_kylin_cube_ii");
    }

    private CubeDesc getTestKylinCubeWithoutSeller() {
        return getCubeDescManager().getCubeDesc("test_kylin_cube_without_slr_desc");
    }

    private CubeDesc getTestKylinCubeWithSeller() {
        return getCubeDescManager().getCubeDesc("test_kylin_cube_with_slr_desc");
    }

    @Before
    public void setUp() throws Exception {
        this.createTestMetadata();
        MetadataManager.removeInstance(this.getTestConfig());
    }

    @After
    public void after() throws Exception {
        this.cleanupTestMetadata();
    }

    @Test
    public void testIsValid() {

        CubeDesc cube = getTestKylinCubeWithSeller();

        // base
        assertEquals(false, Cuboid.isValid(cube, 0));
        assertEquals(true, Cuboid.isValid(cube, toLong("111111111")));

        // mandatory column
        assertEquals(false, Cuboid.isValid(cube, toLong("011111110")));
        assertEquals(false, Cuboid.isValid(cube, toLong("100000000")));

        // zero tail
        assertEquals(true, Cuboid.isValid(cube, toLong("111111000")));

        // aggregation group & zero tail
        assertEquals(true, Cuboid.isValid(cube, toLong("110000111")));
        assertEquals(true, Cuboid.isValid(cube, toLong("110111000")));
        assertEquals(true, Cuboid.isValid(cube, toLong("111110111")));
        assertEquals(false, Cuboid.isValid(cube, toLong("111110001")));
        assertEquals(false, Cuboid.isValid(cube, toLong("111110100")));
        assertEquals(false, Cuboid.isValid(cube, toLong("110000100")));
    }

    @Test
    public void testCuboid1() {
        CubeDesc cube = getTestKylinCubeWithoutSeller();
        Cuboid cuboid;

        cuboid = Cuboid.findById(cube, 0);
        assertEquals(toLong("10000001"), cuboid.getId());

        cuboid = Cuboid.findById(cube, 1);
        assertEquals(toLong("10000001"), cuboid.getId());

        cuboid = Cuboid.findById(cube, toLong("00000010"));
        assertEquals(toLong("10000010"), cuboid.getId());

        cuboid = Cuboid.findById(cube, toLong("00100000"));
        assertEquals(toLong("10100000"), cuboid.getId());

        cuboid = Cuboid.findById(cube, toLong("01001000"));
        assertEquals(toLong("11111000"), cuboid.getId());

        cuboid = Cuboid.findById(cube, toLong("01000111"));
        assertEquals(toLong("11111111"), cuboid.getId());

        cuboid = Cuboid.findById(cube, toLong("11111111"));
        assertEquals(toLong("11111111"), cuboid.getId());
    }

    @Test
    public void testIsValid2() {
        CubeDesc cube = getTestKylinCubeWithoutSeller();
        try {
            assertEquals(false, Cuboid.isValid(cube, toLong("111111111")));
            fail();
        } catch (IllegalArgumentException ex) {
            // expected
        }

        // base
        assertEquals(false, Cuboid.isValid(cube, 0));
        assertEquals(true, Cuboid.isValid(cube, toLong("11111111")));

        // aggregation group & zero tail
        assertEquals(true, Cuboid.isValid(cube, toLong("10000111")));
        assertEquals(false, Cuboid.isValid(cube, toLong("10001111")));
        assertEquals(false, Cuboid.isValid(cube, toLong("11001111")));
        assertEquals(true, Cuboid.isValid(cube, toLong("10000001")));
        assertEquals(true, Cuboid.isValid(cube, toLong("10000101")));

        // hierarchy
        assertEquals(true, Cuboid.isValid(cube, toLong("10100000")));
        assertEquals(true, Cuboid.isValid(cube, toLong("10110000")));
        assertEquals(true, Cuboid.isValid(cube, toLong("10111000")));
        assertEquals(false, Cuboid.isValid(cube, toLong("10001000")));
        assertEquals(false, Cuboid.isValid(cube, toLong("10011000")));
    }

    @Test
    public void testCuboid2() {
        CubeDesc cube = getTestKylinCubeWithSeller();
        Cuboid cuboid;

        cuboid = Cuboid.findById(cube, 0);
        assertEquals(toLong("100100000"), cuboid.getId());

        cuboid = Cuboid.findById(cube, 1);
        assertEquals(toLong("100000111"), cuboid.getId());

        cuboid = Cuboid.findById(cube, toLong("010"));
        assertEquals(toLong("100000111"), cuboid.getId());

        cuboid = Cuboid.findById(cube, toLong("0100000"));
        assertEquals(toLong("100100000"), cuboid.getId());

        cuboid = Cuboid.findById(cube, toLong("1001000"));
        assertEquals(toLong("101111000"), cuboid.getId());

        cuboid = Cuboid.findById(cube, toLong("1000111"));
        assertEquals(toLong("101000111"), cuboid.getId());

        cuboid = Cuboid.findById(cube, toLong("111111111"));
        assertEquals(toLong("111111111"), cuboid.getId());
    }

    //@Test
    public void testII() {
        CubeDesc cube = getTestKylinCubeII();
        assertEquals(toLong("111111111"), Cuboid.getBaseCuboidId(cube));
        assertEquals(true, Cuboid.isValid(cube, toLong("111111111")));
        assertEquals(false, Cuboid.isValid(cube, toLong("111111011")));
        assertEquals(false, Cuboid.isValid(cube, toLong("101011011")));
        assertEquals(false, Cuboid.isValid(cube, toLong("000000000")));
    }
}
