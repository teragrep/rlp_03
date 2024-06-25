/*
 * Java Reliable Event Logging Protocol Library Server Implementation RLP-03
 * Copyright (C) 2021-2024 Suomen Kanuuna Oy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 * Additional permission under GNU Affero General Public License version 3
 * section 7
 *
 * If you modify this Program, or any covered work, by linking or combining it
 * with other code, such other code is not for that reason alone subject to any
 * of the requirements of the GNU Affero GPL version 3 as long as this Program
 * is the same Program as licensed from Suomen Kanuuna Oy without any additional
 * modifications.
 *
 * Supplemented terms under GNU Affero General Public License version 3
 * section 7
 *
 * Origin of the software must be attributed to Suomen Kanuuna Oy. Any modified
 * versions must be marked as "Modified version of" The Program.
 *
 * Names of the licensors and authors may not be used for publicity purposes.
 *
 * No rights are granted for use of trade names, trademarks, or service marks
 * which are in The Program if any.
 *
 * Licensee must indemnify licensors and authors for any liability that these
 * contractual assumptions impose on licensors and authors.
 *
 * To the extent this program is licensed as part of the Commercial versions of
 * Teragrep, the applicable Commercial License may apply to this file if you as
 * a licensee so wish it.
 */
package com.teragrep.rlp_03.version;

import com.teragrep.rlp_03.frame.delegate.event.RelpEventOpen;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

public class VersionImpl implements Version {

    private static final Logger LOGGER = LoggerFactory.getLogger(VersionImpl.class);

    private final String resourceLocation;
    private final String tagProperty;

    public VersionImpl() {
        this("/com.teragrep.rlp_03.version.properties");
    }

    public VersionImpl(String resourceLocation) {
        this(resourceLocation, "version");
    }

    public VersionImpl(String resourceLocation, String tagProperty) {
        this.resourceLocation = resourceLocation;
        this.tagProperty = tagProperty;
    }

    @Override
    public String tag() {
        try (InputStream is = RelpEventOpen.class.getResourceAsStream(resourceLocation)) {
            Properties properties = new Properties();
            properties.load(is);
            return properties.getProperty(tagProperty);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
