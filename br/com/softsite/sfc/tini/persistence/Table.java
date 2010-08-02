/*
* Project: SoftSite Foundation Classes (SFC) - SFC Tini
* Date   : 23/03/2004
*
* Copyright (c) 1996-2004 SoftSite Tecnologia
* Todos os direitos reservados
*/
package br.com.softsite.sfc.tini.persistence;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.Vector;



/**
* Table é a classe que gerencia persistência obedecendo as limitações do TINI
*
* Essa classe foi baseada na biblioteca pública javadbf de autoria de anil@linuxense,
* baseada na licença LGPL (http://www.gnu.org/copyleft/lesser.html)
*
* @author  regismelo@softsite.com.br
* @version 1.0
*/
public class Table {

	/** Nome da tabela referenciada */
	public final String tableName;

	/** Assinatura da tabela - versão a qual ela pertence (DBase III Plus, FoxBase, ...) */
	private byte signature;

	/** Asssinatura de uma tabela do DBase III Plus sem campos memo */
	public static final byte SIGNATURE_DBASE_III_PLUS_NO_MEMO 			= 0x03;

	/** Indica o fim do cabeçalho do DBF */
	static final byte HEADER_RECORD_TERMINATOR 							= 0x0D;	 
	
	/** Indica o final do arquivo */
	static final byte EOF												= 0x1D;  // 29
	
	/** Indica que o registro está deletado */
	static final byte DELETED										    = '*';
	
	/** Posicao do cabecalho que contém a qtde de registros da tabela */
	static final byte HEADER_NUMBER_OF_RECORDS						 	= 4;
	
	/** Indica que esse DBF não está associado a um database */
	static final byte HEADER_NOT_ASSOCIATED_DATABASE					= 0x00; 

	/** Ano da última modificação da tabela */
	private byte year;

	/** Mês da última modificação da tabela */
	private byte month;

	/** Dia da última modificação da tabela */
	private byte day;

	/** Número de registros na tabela */
	private int numberOfRecords;

	/** Registro atual */
	private int recordNumber;

	/** Tamanho do header */
	public short headerLength;

	/** Tamanho do registro */
	public short recordLength;

	/** Arquivo aberto? */
	private boolean isOpen;

	/** Stream que representa a tabela */
	private RandomAccessFile fileStream;

	/** Colunas */
	private Field[] fields;

	/** Guarda o conteúdo do registro atual */
	private byte recordData[];
	
	/** Ler dados marcados como deletados? */
	private boolean readDeletedData = false;


	/**
	 * Construtor
	 *
	 * @param tableName Nome da tabela a ser aberta
	 * @throws TableNotFoundException Indica que a tabela não foi encontrada.
	 */
	public Table( String tableName ) throws FileNotFoundException, IOException, TableCorruptException
	{
		super();
		this.tableName = tableName;

		openTable();
	}



	/**
	 * Abrir uma tabela e popular os dados relacionados ao cabeçalho
	 */
	private void openTable() throws FileNotFoundException, IOException, TableCorruptException
	{
		// Estrutura do cabeçalho da tabela
		//
		//
		// Byte Offset			Description
		// --------------------------------------------------------------------------------------------------
		// 00 					Tipo do arquivo a ser aberto
		//							0x02   FoxBASE
		//							0x03   FoxBASE+/Dbase III plus, no memo
		//							0x30   Visual FoxPro
		//							0x31   Visual FoxPro, autoincrement enabled
		//							0x43   dBASE IV SQL table files, no memo
		//							0x63   dBASE IV SQL system files, no memo
		//							0x83   FoxBASE+/dBASE III PLUS, with memo
		//							0x8B   dBASE IV with memo
		//							0xCB   dBASE IV SQL table files, with memo
		//							0xF5   FoxPro 2.x (or earlier) with memo
		//							0xFB   FoxBASE
		//
		//						Iremos utirlizar sempre 0x03 que é o formato mais limitado (DBase III plus)
		//
		// 1 - 3				Último atualização do arquivo no formato YYMMDD
		// 4 - 7 				Número de registros no arquivo
		// 8 - 9				Tamanho do cabeçalho
		// 10 - 11				Tamanho de um registro, incluindo o byte de deletado
		// 12 - 27				Reservado
		// 28					Table flags:
		//							0x01   file has a structural .cdx
		//							0x02   file has a Memo field
		//							0x04   file is a database (.dbc)
		//							This byte can contain the sum of any of the above values.
		//							For example, the value 0x03 indicates the table has a structural .cdx
		//							and a Memo field.
		// 29					Code page mark
		// 30-31				Reservado
		// 32-n					Informações dos registros
		// n+1 					Header record terminator (0x0D)
		// n+2 to n+264			A 263-byte range that contains the backlink, which is the relative path of an
		//						associated database (.dbc) file, information. If the first byte is 0x00, the file
		//						is not associated with a database. Therefore, database files always contain 0x00.
		//						--> Esse dado é ignorado por essa classe.

		fileStream	    		= new RandomAccessFile(new File(tableName), "rw");

		signature 				= fileStream.readByte(); 					/* 0 */
		year 					= fileStream.readByte();      				/* 1 */
		month 					= fileStream.readByte();     				/* 2 */
		day 					= fileStream.readByte();       				/* 3 */
		numberOfRecords 		= Utils.readLittleEndianInt(fileStream);   	/* 4-7 */
		headerLength 			= Utils.readLittleEndianShort(fileStream); 	/* 8-9 */
		recordLength 			= Utils.readLittleEndianShort(fileStream); 	/* 10-11 */

		//--- Desprezar dados não utilizados por essa classe ---

		// Reservado 1
		Utils.readLittleEndianShort(fileStream);      		/* 12-13 */

		// Transação incompleta
		fileStream.readByte();           					/* 14 */

		// Dados encriptados
		fileStream.readByte();                  			/* 15 */

		// freeRecordThread???
		Utils.readLittleEndianInt(fileStream); 				/* 16-19 */

		// Reservado
		fileStream.readInt();                          		/* 20-23 */

		// Reservado
		fileStream.readInt();                         		/* 24-27 */

		// MDX, CDX Flag
		fileStream.readByte();                         		/* 28 */

		// Code Page
		fileStream.readByte();                    			/* 29 */

		// Reservado
		Utils.readLittleEndianShort(fileStream);       		/* 30-31 */

		// Ler dados da estrutura das colunas...
		readRecordStructure();


		isOpen 		 = true;
		recordNumber = 0;

	}

	/**
	 * Ler a estrutura das colunas
	 */
	private void readRecordStructure() throws IOException, TableCorruptException
	{
		Vector fieldsVector = new Vector();
		Field field = Field.createField(fileStream);

		while( field != null)
		{
			fieldsVector.addElement(field);
			field = Field.createField(fileStream);
		}

		// Esse laço move os dados do vector para um array. Isso é feito pois operações com o
		// vector são mais lentas que operação com o array. (Consideração válida pois o TINI possui
		// limitações de processamento)
		fields = new Field[fieldsVector.size()];

		for( int i = 0; i < fields.length; i++ )
		{
			fields[i] = (Field)fieldsVector.elementAt(i);
		}

		fieldsVector = null;

		// Rotina para pular o resto do cabeçalho (n+2 to n+264)
		int dataStartIndex = this.headerLength - ( 32 + (32* fields.length )) - 1;
		if( dataStartIndex > 0) {

			fileStream.skipBytes( dataStartIndex);
		}
	}


	/**
	 * Exibir dados do objeto como uma String
	 */
	public String toString()
	{

		StringBuffer sb = new StringBuffer(  year + "/" + month + "/" + day + "\n"
		+ "Total records: " + numberOfRecords +
		"\nHeader length: " + headerLength +
		"\n");

		for( int i=0; i<fields.length; i++) {
			sb.append( fields[i].fieldName );
			sb.append( "\n");
		}

		return sb.toString();
	}

	/**
	 * Fechar a tabela
	 * @throws IOException
	 */
	public void close() throws IOException
	{
		if ( isOpen  )
		{
			fileStream.close();
			this.isOpen = false;
		}
	}

	/**
	 * Avança o ponteiro para o próximo registro
	 * @return O número do próximo registro
	 * @throws IOException
	 */
	public boolean nextRecord() throws IOException 
	{
		if ( this.recordNumber == this.numberOfRecords )
		{
			return false;
		}
		
		recordNumber++;
		this.readRecordData();

		return true;
		
	}
	
	/**
	 * Retrocede o ponteiro para o próximo registro
	 * @return O número do próximo registro
	 * @throws IOException
	 */
	public boolean priorRecord() throws IOException
	{
		if ( this.recordNumber == 1 )
		{
			return false;
		}
		
		this.skip(-1);

		return true;

	}
	
	/**
	 * Atribui o valor da coluna String passada como parâmetro do registro corrente.
	 * @param columnName Nome da coluna que se quer obter o conteúdo
	 * @return Valor da coluna
	 * @throws FieldNotFoundException
	 */
	public void setFieldString( String columnName, String valor ) throws FieldNotFoundException, FieldTypeException, IOException
	{
		int i = locatePointer( columnName, Field.TYPE_CHARACTER );
		fileStream.write(this.fields[i].formatData(valor));
		
		// Reposicionar ponteiro...
		goTo(recordNumber);
			
		return;
	}

	/**
	 * Atribui o valor da coluna int passada como parâmetro do registro corrente.
	 * @param columnName Nome da coluna que se quer obter o conteúdo
	 * @return Valor da coluna
	 * @throws FieldNotFoundException
	 */
	public void setFieldInteger( String columnName, int valor ) throws FieldNotFoundException, FieldTypeException, IOException
	{
		int i = locatePointer( columnName, Field.TYPE_NUMERIC );
		fileStream.write(this.fields[i].formatData(new Integer(valor)));
		
		// Reposicionar ponteiro...
		goTo(recordNumber);
			
		return;
	}

	/**
	 * Atribui o valor da coluna double passada como parâmetro do registro corrente.
	 * @param columnName Nome da coluna que se quer obter o conteúdo
	 * @return Valor da coluna
	 * @throws FieldNotFoundException
	 */
	public void setFieldDouble( String columnName, double valor ) throws FieldNotFoundException, FieldTypeException, IOException
	{
		int i = locatePointer( columnName, Field.TYPE_FLOAT );
		fileStream.write(this.fields[i].formatData(new Double(valor)));
		
		// Reposicionar ponteiro...
		goTo(recordNumber);
			
		return;
	}

	/**
	 * Atribui o valor da coluna Date passada como parâmetro do registro corrente.
	 * @param columnName Nome da coluna que se quer obter o conteúdo
	 * @return Valor da coluna
	 * @throws FieldNotFoundException
	 */
	public void setFieldDate( String columnName, Date valor ) throws FieldNotFoundException, FieldTypeException, IOException
	{
		int i = locatePointer( columnName, Field.TYPE_DATE );
		fileStream.write(this.fields[i].formatData(valor));
		
		// Reposicionar ponteiro...
		goTo(recordNumber);
			
		return;
	}

	/**
	 * Retorna o valor da coluna String passada como parâmetro do registro corrente.
	 * @param columnName Nome da coluna que se quer obter o conteúdo
	 * @return Valor da coluna
	 * @throws FieldNotFoundException
	 */
	public int getFieldInteger( String columnName ) throws FieldNotFoundException, FieldTypeException
	{
		int skipBytes = 0;
		int i;
		for ( i = 0; ( i < this.fields.length ) && ( ! ( this.fields[i].fieldName.equalsIgnoreCase(columnName) ) ); i++ )
		{
			skipBytes += this.fields[i].fieldLength;
		}

		// Se o dado não foi encontrado...
		if ( i == this.fields.length )
		{
			throw new FieldNotFoundException();
		}

		// Se o dado não é do mesmo tipo...
		if ( this.fields[i].dataType != Field.TYPE_NUMERIC )
		{
			throw new FieldTypeException();
		}

		String number = new String(recordData, skipBytes, this.fields[i].fieldLength).trim();
		return Integer.parseInt( number );
	}
	
	/**
	 * Retorna o valor da coluna String passada como parâmetro do registro corrente.
	 * @param columnName Nome da coluna que se quer obter o conteúdo
	 * @return Valor da coluna
	 * @throws FieldNotFoundException
	 */
	public String getFieldString( String columnName ) throws FieldNotFoundException, FieldTypeException
	{
		int skipBytes = 0;
		int i;
		for ( i = 0; ( i < this.fields.length ) && ( ! ( this.fields[i].fieldName.equalsIgnoreCase(columnName) ) ); i++ )
		{
			skipBytes += this.fields[i].fieldLength;
		}

		// Se o dado não foi encontrado...
		if ( i == this.fields.length )
		{
			throw new FieldNotFoundException();
		}

		// Se o dado não é do mesmo tipo...
		if ( this.fields[i].dataType != Field.TYPE_CHARACTER )
		{
			throw new FieldTypeException();
		}

		String retorno = new String(recordData, skipBytes, this.fields[i].fieldLength).trim();
		return retorno;
	}	

	/**
	 * Retorna o valor da coluna Double passada como parâmetro do registro corrente.
	 * @param columnName Nome da coluna que se quer obter o conteúdo
	 * @return Valor da coluna
	 * @throws FieldNotFoundException
	 */
	public double getFieldDouble( String columnName ) throws FieldNotFoundException, FieldTypeException
	{
		int skipBytes = 0;
		int i;
		for ( i = 0; ( i < this.fields.length ) && ( ! ( this.fields[i].fieldName.equalsIgnoreCase(columnName) ) ); i++ )
		{
			skipBytes += this.fields[i].fieldLength;
		}

		// Se o dado não foi encontrado...
		if ( i == this.fields.length )
		{
			throw new FieldNotFoundException();
		}

		// Se o dado não é do mesmo tipo...
		if ( this.fields[i].dataType != Field.TYPE_NUMERIC )
		{
			throw new FieldTypeException();
		}

		String s = new String(recordData, skipBytes, this.fields[i].fieldLength).trim();
		
		// Testar o caso em que o campo não está preenchido - uma espécie de NULL do DBF
		if ( s.length() == 0 )
		{
			return 0;
		}
		return Double.valueOf( s  ).doubleValue();
	}

	/**
	 * Retorna o valor da coluna Date passada como parâmetro do registro corrente.
	 * @param columnName Nome da coluna que se quer obter o conteúdo
	 * @return Valor da coluna
	 * @throws FieldNotFoundException
	 */
	public Date getFieldDate( String columnName ) throws FieldNotFoundException, FieldTypeException
	{
		int skipBytes = 0;
		int i;
		for ( i = 0; ( i < this.fields.length ) && ( ! ( this.fields[i].fieldName.equalsIgnoreCase(columnName) ) ); i++ )
		{
			skipBytes += this.fields[i].fieldLength;
		}

		// Se o dado não foi encontrado...
		if ( i == this.fields.length )
		{
			throw new FieldNotFoundException();
		}

		// Se o dado não é do mesmo tipo...
		if ( this.fields[i].dataType != Field.TYPE_DATE )
		{
			throw new FieldTypeException();
		}

		byte year[]  = new byte[4];
		byte month[] = new byte[2];
		byte day[]	 = new byte[2];

		// Mover dados para as variáveis year, month e day.
		// Poderíamos usar os métodos da biblioteca com.dalsemi.system.ArrayUtils,
		// mas tornaríamos o código dependente do TINI (não seria possívei testar no desktop)
		// TODO: Se esse ponto for um gargalo, ele poderá ser otimizado utilizano os métodos nativos dessa biblioteca
		year[0]  = recordData[skipBytes + 0 ];
		year[1]  = recordData[skipBytes + 1 ];
		year[2]  = recordData[skipBytes + 2 ];
		year[3]  = recordData[skipBytes + 3 ];

		month[0] = recordData[skipBytes + 4 ];
		month[1] = recordData[skipBytes + 5 ];

		day[0]   = recordData[skipBytes + 6 ];
		day[1]   = recordData[skipBytes + 7 ];

		GregorianCalendar calendar = new GregorianCalendar(
										Integer.parseInt( new String( year)),
										Integer.parseInt( new String( month)) - 1,
										Integer.parseInt( new String( day))
									);

		return calendar.getTime();
	}
	

	/**
	 * Posiciona o ponteiro no primeiro registro
	 * @throws IOException 
	 */
	public void goTop() throws IOException
	{
		if ( isOpen )
		{
			fileStream.seek(headerLength);
			this.recordNumber = 1;
			this.readRecordData();			
		}
						
	}
	
	/**
	 * Posiciona no último registro
	 * @throws IOException
	 */
	public void goBottom() throws IOException
	{		
		skip( this.numberOfRecords - this.recordNumber );		
	}

	
	/**
	 * Pula <n> registros. O valor de skip pode ser negativo.
	 * @param records
	 * @throws IOException
	 */
	public void skip( int records ) throws IOException
	{
		int newRecordPos = recordNumber + records;
		
		if ( newRecordPos < 0 )
		{
			newRecordPos = 0;
		}
		else if ( newRecordPos > numberOfRecords )
		{
			newRecordPos = numberOfRecords - 1;
		}
		
		fileStream.seek( headerLength + ( (newRecordPos-1) * recordLength));
		this.recordNumber = newRecordPos;
		this.readRecordData();		
	}
	
	/**
	 * Posiciona em um determinado registro
	 * @param record Número do registro a posicionar. 
	 * 				 Se for maior que a qtde de registros do arquivo, ponteiro é posicionado no último registro.
	 * 				 Se for menor, ponteiro é posicionado no primeiro registro. 
	 * @throws IOException
	 */
	public void goTo( int record ) throws IOException
	{
		if ( record > numberOfRecords )
		{ 
			record = recordNumber;
		}
		else if ( record < 0 )
		{
			record = 1;
		}
		
		fileStream.seek( headerLength + ( (record-1) * recordLength));
		this.recordNumber = record;
		this.readRecordData();		
	}

	/**
	 * Ler o registro
	 * @throws IOException
	 */
	private void readRecordData() throws IOException
	{
		
		// Pular os registros deletados...
		int deleted;

		// Loop para posicionar no primeiro registro não deletado...
		boolean isDeleted = false;
		do
		{
			if(isDeleted)
			{
				fileStream.skipBytes( this.recordLength-1);
				recordNumber++;
			}

			deleted = fileStream.readByte();
			
			// Chegou no final do arquivo?
			if( deleted == EOF )
			{
				return;
			}
			
			if ( readDeletedData )
			{
				break;
			}

			isDeleted = (  deleted == '*' );
		} while( isDeleted);
		
		// Ler array do tamanho do registro...
		this.recordData = new byte[this.recordLength-1];
		fileStream.read( this.recordData );		
	}	
	
	/**
	 * Obtém o número de registros dessa tabela
	 * @return Número de registros dessa tabela
	 */
	public int getNumberOfRecords() {
		return numberOfRecords;
	}

	/**
	 * Obtém o número do registro atual 
	 * @return Número do registro atual
	 */
	public int getRecordNumber() {
		return recordNumber;
	}

	/**
	 * Indica se a classe irá ler dados marcados como deletados
	 * @return
	 */
	public boolean isReadDeletedData() {
		return readDeletedData;
	}

	/**
	 * Indica se a classe irá ler dados marcados como deletados
	 * @param b TRUE indica que a classe irá considerar registros deletados
	 */
	public void setReadDeletedData(boolean b) {
		readDeletedData = b;
	}

	/**
	 * Adicionar um registro a tabela
	 * TODO: Refatorar para usar fileStream
	 * @param data Hashtable contendo o para <nome da coluna> / <valor>. Nome das colunas é sensitive case!
	 * @throws IOException
	 */
	public void addRecord( Hashtable data ) throws IOException
	{		
		Field field; 
		byte dataRecord[] = new byte[this.recordLength];
		byte dataColumn[];
		int  pos = 1; 
		
		dataRecord[0] = 32; // Indicar que o registro não está deletado...
				
		// Loop nos fields...
		for( int i = 0, len = fields.length; i < len; i++)
		{
			field = fields[i];
			
			dataColumn = field.formatData(data.get(field.fieldName));
			
			for ( int j = 0; j < dataColumn.length; j++ )
			{
				dataRecord[pos++] = (byte)dataColumn[j];
			}			
			
			//System.out.println( field.fieldName + " ==> [" + dataColumn.length + "]" + new String(dataColumn) );
		}
		
		
		if ( numberOfRecords == 0 )
		{
			fileStream.seek((int)fileStream.length()-2);
			fileStream.write(HEADER_NOT_ASSOCIATED_DATABASE);			
		}
		else
		{
			fileStream.seek((int)fileStream.length()-1);
		}
		
		fileStream.write(dataRecord);
		fileStream.write( EOF );
						
		numberOfRecords++;
		
		this.changeHeaderNumberOfRecords();
		this.goBottom();
			
	}
	
	/**
	 * Marcar um registro como deletado
	 * @param record Número do registro a deletar
	 * @throws IOException
	 */
	public void deleteRecord(int record) throws IOException
	{
		// Posicionar o ponteiro do arquivo na posição correta...
		// Cabecalho + ( posicao do registro anterior * tamanho do registro )
		long pos = headerLength + ( ( record - 1 ) * recordLength );
		fileStream.seek(pos);
		recordNumber = record;	
		fileStream.write( Table.DELETED );		
	}

	
	private int locatePointer(String columnName,  char dataType ) throws FieldNotFoundException, FieldTypeException, IOException
	{
		int skipBytes = 0;
		int i;
		for ( i = 0; ( i < this.fields.length ) && ( ! ( this.fields[i].fieldName.equalsIgnoreCase(columnName) ) ); i++ )
		{
			skipBytes += this.fields[i].fieldLength;
		}

		// Se o dado não foi encontrado...
		if ( i == this.fields.length )
		{
			throw new FieldNotFoundException();
		}

		// Se o dado não é do mesmo tipo...
		if ( this.fields[i].dataType != dataType )
		{
			// Cuidado! Verificar o caso do tipo Float/Numérico - No DBF para Dbase III não existe
			// um campo do Tipo Double - Campos de valores são sempre retornados como numeric
			if ( ! ( this.fields[i].dataType == Field.TYPE_NUMERIC && dataType == Field.TYPE_FLOAT ) )
			{
				throw new FieldTypeException();
			}
		}
		
		// Posicionar o ponteiro do arquivo na posição correta...
		// Cabecalho + ( posicao do registro anterior * tamanho do registro ) + bytes a pular da coluna + byte deletado
		long pos = headerLength + ( ( recordNumber - 1 ) * recordLength ) + skipBytes + 1;
		fileStream.seek(pos);
		
		return i;
	}
	
	/**
	 * Atualiza a informação no cabeçalho do número de registros contido na tabela
	 * 
	 * @throws IOException
	 */
	private void changeHeaderNumberOfRecords() throws IOException
	{
		// Posiciona na entrada do cabeçalho que contém o número de registros...
		this.fileStream.seek( HEADER_NUMBER_OF_RECORDS );
		
		// Transforma o número de registros em um little endian...
		//int i = Utils.littleEndian( numberOfRecords );
		//fileStream.write(i);
		
		byte b = (byte)( numberOfRecords & 0xFF );
		fileStream.write(b);
		
		b = (byte)( numberOfRecords & 0xFF00 );
		fileStream.write(b);
		
		b = (byte)( numberOfRecords & 0xFF0000 );
		fileStream.write(b);
		
		b = (byte)( numberOfRecords & 0xFF000000 );
		fileStream.write(b);
	}

/*	public static void main(String[] args) throws FileNotFoundException, IOException, TableCorruptException, FieldNotFoundException, FieldTypeException {
		Hashtable recebimento = new Hashtable();
		Table recebimentoTable;
		recebimentoTable = new Table("C:/dCLIP41/RECEB2.DBF");
		recebimento.put("DS_CMC7", "123456789123456789");
		recebimento.put("NR_CPF", "54214459334");
		recebimento.put("DT_VENC", new Date("2004/05/20"));

		recebimento.put("CD_RECEB", "2");
		recebimento.put("CD_CLIENTE", new Integer(15));
		recebimento.put("VR_RECEB", "5000");
		recebimento.put("ID_TRANSM", "0");
		recebimentoTable.addRecord(recebimento);
		recebimentoTable.close();		

		recebimentoTable = new Table("C:/dCLIP41/RECEB2.DBF");
		recebimentoTable.goTop();	
		double valorDinheiro = 0, valorCheque = 0;
		String cheques = "";
		do {
			if (recebimentoTable.getFieldInteger("CD_CLIENTE") == 1){
				if (recebimentoTable.getFieldString("NR_CPF").equals("")){
					valorDinheiro = valorDinheiro + recebimentoTable.getFieldDouble("VR_RECEB"); 
				} else{
					valorCheque = valorCheque + recebimentoTable.getFieldDouble("VR_RECEB");
					cheques = cheques + recebimentoTable.getFieldString("NR_CPF") + '\t' +
										recebimentoTable.getFieldDate("DT_VENC") + '\t' +
										recebimentoTable.getFieldDouble("VR_RECEB") + '\t';  
				}
			}
		} while (recebimentoTable.nextRecord());
		recebimentoTable.close();
		System.out.println("ValorDinheiro:" + valorDinheiro);
		System.out.println("ValorCheque:" + valorCheque);
		System.out.println("cheques:" + cheques);									
		
	}*/
			
}
