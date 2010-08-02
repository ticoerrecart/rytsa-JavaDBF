/*
* Project: DBFProject
* Date   : Mar 28, 2004
*
* Copyright (c) 2004 SoftSite Tecnologia
* Todos os direitos reservados
*/
package br.com.softsite.sfc.tini.persistence;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Calendar;
import java.util.Date;

/**
* Encapsula os dados relativos a um campo
*
* @author  regismelo
* @version 1.0
*/
public class Field {

	String fieldName; 					/* 0-10*/
	char   dataType;					/* 11 */
	int	   fieldLength;					/* 16 */
	byte   decimalCount;                /* 17 */

	// Tipos das colunas
	public static final char TYPE_CHARACTER 	= 'C';
	public static final char TYPE_DATE 			= 'D';
	public static final char TYPE_FLOAT			= 'F';
	public static final char TYPE_NUMERIC		= 'N';
	public static final char TYPE_LOGICAL		= 'L';
	public static final char TYPE_MEMO			= 'M';

	/**
	 * Cria um novo field baseado em um stream já aberto.
	 *
	 * Importante --> Considera-se que a stream está posicionada corretamente. Esse método é tipicamente
	 * 				  chamado pela classe Table
	 *
	 * @param dataInputStream
	 * @return Field 					Uma nova instância de Field devidamente populada
	 * @throws IOException				Algum erro de IO aconteceu
	 * @throws TableCorruptException	O stream não obedece o formato esperado
	 */
	static Field createField(RandomAccessFile dataInputStream ) throws IOException, TableCorruptException
	{
		Field field = new Field();

		//	Estrutura dos campos da tabela
		//
		//
		// Byte Offset			Description
		// --------------------------------------------------------------------------------------------------
		// 0–10  				Field name with a maximum of 10 characters.
		//						If less than 10, it is padded with null characters (0x00).
		//
		// 11 					Field type:
		//							C   –   Character
		//							Y   –   Currency
		//							N   –   Numeric
		//							F   –   Float
		//							D   –   Date
		//							T   –   DateTime
		//							B   –   Double
		//							I   –   Integer
		//							L   –   Logical
		//							M   – Memo
		//							G   – General
		//							C   –   Character (binary)
		//							M   –   Memo (binary)
		//							P   –   Picture
		// 12 – 15 				Displacement of field in record
		// 16 					Length of field (in bytes)
		// 17 					Number of decimal places
		// 18 					Field flags:
		//							0x01   System Column (not visible to user)
		//							0x02   Column can store null values
		//							0x04   Binary column (for CHAR and MEMO only)
		//							0x06   (0x02+0x04) When a field is NULL and binary (Integer, Currency, and Character/Memo fields)
		//							0x0C   Column is autoincrementing
		// 19 - 22 				Value of autoincrement Next value
		// 23 					Value of autoincrement Step value
		// 24 – 31 Reserved

		byte[] fieldNameTemp = new byte[11];
		fieldNameTemp[0] = dataInputStream.readByte();


		// Verificar
		if ( fieldNameTemp[0] == Table.HEADER_RECORD_TERMINATOR )
		{
			// Oops. O final do record foi encontrado nesse momento!
			return null;
		}

		// Ler o nome do campo  0 - 10 (o byte 1 já foi lido anteriormente)
		dataInputStream.read( fieldNameTemp, 1, 10);	/* 1-10 */

		// Independente do tamanho do campo, a estrutura do DBF sempre guarda 11 posições.
		// Laço para identificar o tamanho real do campo
		for( int i=0; i<fieldNameTemp.length; i++) {

			if( fieldNameTemp[i] == (byte)0)
			{
				field.fieldName = new String( fieldNameTemp ).substring(0,i);
				break;
			}
		}



		// Tipo do dado
		field.dataType = (char)dataInputStream.readByte(); 		/* 11 */

		// Reservado
		Utils.readLittleEndianInt(dataInputStream);				/* 12-15 */

		// Tamanho do campo
		field.fieldLength = dataInputStream.readUnsignedByte();  /* 16 */

		// Qtde de decimais
		field.decimalCount = dataInputStream.readByte();		 /* 17 */

		// Os demais bytes não são lidos por essa classe...
		dataInputStream.skipBytes( 14 );


		//--- Código original da JavaDBF
//		field.dataType = in.readByte(); /* 11 */
//		field.reserv1 = Utils.readLittleEndianInt( in); /* 12-15 */
//		field.fieldLength = in.readUnsignedByte();  /* 16 */
//		field.decimalCount = in.readByte(); /* 17 */
//		field.reserv2 = Utils.readLittleEndianShort( in); /* 18-19 */
//		field.workAreaId = in.readByte(); /* 20 */
//		field.reserv2 = Utils.readLittleEndianShort( in); /* 21-22 */
//		field.setFieldsFlag = in.readByte(); /* 23 */
//		in.read( field.reserv4); /* 24-30 */
//		field.indexFieldFlag = in.readByte(); /* 31 */


		return field;
	}
	
	/**
	 * Formatar a coluna de acordo com o tipo
	 * @param o Objeto com o tip do dado 
	 * @return array de caracteres com o dado formatado
	 */
	public byte[] formatData( Object o )
	{
		byte c[] = new byte[this.fieldLength];
		
		if ( o == null )
		{
			return arrayCopy( null, c, dataType );
		}
		
		switch ( this.dataType )
		{
			case Field.TYPE_CHARACTER  :
			{
				return arrayCopy( ((String)o).getBytes(), c, dataType );
			}		
			case Field.TYPE_FLOAT : 
			case Field.TYPE_NUMERIC :
			{
				return arrayCopy( o.toString().getBytes(), c, dataType );
			}			
			case Field.TYPE_DATE : 
			{
				Calendar calendar = Calendar.getInstance();
				calendar.setTime((Date)o);
				byte[] dc;

				int year  = calendar.get( Calendar.YEAR );
				int month = calendar.get( Calendar.MONTH )+1;
				int day	  = calendar.get( Calendar.DAY_OF_MONTH );
				
				dc = ( "" + 
					   year + 
					   ( month < 10 ? "0" + month : "" + month ) +					   
					   ( day < 10 ? "0" + day : "" + day )
					 ).getBytes();
					 
				return dc;				
			}
		}

		return null;
		
	}
	
	private byte[] arrayCopy( byte[] source, byte[] dest, char type)
	{
		int sourceLen = 0;
		if ( source != null )
		{
			sourceLen = source.length;
		}
		
		int dif     = dest.length - sourceLen;
		
		if ( dif < 0 )
			dif = 0;
			
		int iniCopy = 0;
		
		if ( type == Field.TYPE_CHARACTER )
		{
			// Preencher de espaços a direita
			iniCopy = 0;
		}
		else
		{
			iniCopy = dif;
		}
		
		// Preencher com espaços...
		for ( int i = 0; i < dest.length; i++ )
		{
			dest[i] = ' ';
		}
		
		// Copiar os dados...
		for ( int i = iniCopy, k = 0; ( i < dest.length && k < sourceLen ); i++, k++ )
		{
			dest[i] = source[k];
			
		}
		
		return dest;
	}

}
