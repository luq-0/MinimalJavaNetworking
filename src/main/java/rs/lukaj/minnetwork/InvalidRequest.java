/*
  Minimal Java Networking - a barebones networking library for Java and Android
  Copyright (C) 2017 Luka Jovičić

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License as published
  by the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package rs.lukaj.minnetwork;

/**
 * Thrown when request is for any reason invalid. Whichever reason that is,
 * it's this app developer's fault.
 *
 * Created by luka on 4.8.17.
 */

public class InvalidRequest extends RuntimeException {
    public InvalidRequest(String msg) {
        super(msg);
    }

    public InvalidRequest(String msg, Throwable cause) {
        super(msg, cause);
    }
}
