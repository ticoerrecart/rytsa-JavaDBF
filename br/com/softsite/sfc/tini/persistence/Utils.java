/*
* Project: DBFProject
* Date   : Mar 27, 2004
*
* Copyright (c) 2004 SoftSite Tecnologia
* Todos os direitos reservados
*/
package br.com.softsite.sfc.tini.persistence;

/**
* Utils
* Class for contining utility functions.
*
* This file is part of JavaDBF packege.
*
*  author: anil@linuxense
*  license: LGPL (http://www.gnu.org/copyleft/lesser.html)
*
*/

import java.io.*;

/**
	Miscelaneous functions required by the JavaDBF package.
*/
public class Utils {

	public static final int ALIGN_LEFT = 10;
	public static final int ALIGN_RIGHT = 12;

	public static int readLittleEndianInt(RandomAccessFile in)
	throws IOException {

		int bigEndian = 0;
		for( int shiftBy=0; shiftBy<32; shiftBy+=8) {

			bigEndian |= (in.readByte()&0xff) << shiftBy;
		}

		return bigEndian;
	}

	public static short readLittleEndianShort(RandomAccessFile in)
	throws IOException {

		int low = in.readByte() & 0xff;
		int high = in.readByte();

		return (short )(high << 8 | low);
	}

	public static byte[] trimLeftSpaces( byte [] arr) {

		StringBuffer t_sb = new StringBuffer( arr.length);

		for( int i=0; i<arr.length; i++) {

			if( arr[i] != ' ') {

				t_sb.append( (char)arr[ i]);
			}
		}

		return t_sb.toString().getBytes();
	}

	public static short littleEndian( short value) {

		short num1 = value;
		short mask = (short)0xff;

		short num2 = (short)(num1&mask);
		num2<<=8;
		mask<<=8;

		num2 |= (num1&mask)>>8;

		return num2;
	}

	public static int littleEndian(int value) {

		int num1 = value;
		int mask = 0xff;
		int num2 = 0x00;

		num2 |= num1 & mask;

		for( int i=1; i<4; i++) {

			num2<<=8;
			mask <<= 8;
			num2 |= (num1 & mask)>>(8*i);
		}

		return num2;
	}

	public static boolean contains( byte[] arr, byte value) {

		boolean found = false;
		for( int i=0; i<arr.length; i++) {

			if( arr[i] == value) {

				found = true;
				break;
			}
		}

		return found;
	}
	
	public static byte[] fillString( String dado, int tamanho )
	{
		byte ret[] = new byte[tamanho];
		char ori[] = dado.toCharArray();
		
		for ( int i = 0; i < tamanho; i++ )
		{
			if ( i < ori.length )
			{
				ret[i] = (byte)ori[i];
			}
			else
			{
				ret[i] = ' ';
			} 
		}
		System.out.println(new String(ret));
		return ret;
	}
	
	
}
