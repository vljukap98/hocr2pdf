import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.lowagie.text.pdf.BaseFont;
import net.htmlparser.jericho.Element;
import net.htmlparser.jericho.Source;
import net.htmlparser.jericho.StartTag;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.CMYKColor;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;


/**
 * @author Federico Tarantino
 *
 * modified by @vljukap98
 */
public class OcrService {
	
	/**
	 * from hocr html and image create a pdf, with itext and jericho
	 * @param hocrFile (html hocr file, generated with tesseract or other ocr software)
	 * @param inputFile (source image file)
	 * @param outputFile (outputstream where write pdf)
	 */
	public static void hocr2pdf(File hocrFile, File inputFile, OutputStream outputFile){
		try {			
			// The resolution of a PDF file (using iText) is 72pt per inch
			float pointsPerInch = 72.0f;
			
			// Using the jericho library to parse the HTML file
			Source source = new Source(hocrFile);

			// Load the image
			Image image = Image.getInstance(inputFile.getAbsolutePath());
			float dotsPerPointX;
			float dotsPerPointY;
			if(image.getDpiX()>0){
				dotsPerPointX = image.getDpiX() / pointsPerInch;
				dotsPerPointY = image.getDpiY() / pointsPerInch;
			} else {
				dotsPerPointX = 1.0f;
				dotsPerPointY = 1.0f;
			}
			
			float pageImagePixelHeight = image.getHeight();
			Document pdfDocument = new Document(new Rectangle(image.getWidth() / dotsPerPointX, image.getHeight() / dotsPerPointY));
			PdfWriter pdfWriter = PdfWriter.getInstance(pdfDocument, outputFile);
			pdfDocument.open();
			// first define a standard font for our text
			Font defaultFont = FontFactory.getFont(FontFactory.TIMES, BaseFont.CP1250, 8, Font.NORMAL, CMYKColor.BLACK);
			
			// Put the text behind the picture (reverse for debugging)
			PdfContentByte cb = pdfWriter.getDirectContentUnder();
			//PdfContentByte cb = pdfWriter.getDirectContent();
			
			image.scaleToFit(image.getWidth() / dotsPerPointX, image.getHeight() / dotsPerPointY);
			image.setAbsolutePosition(0, 0);
			// Put the image in front of the text (reverse for debugging)
			pdfWriter.getDirectContent().addImage(image);
			
			// In order to place text behind the recognised text snippets we are interested in the bbox property		
			Pattern bboxPattern = Pattern.compile("bbox(\\s+\\d+){4}");
			// This pattern separates the coordinates of the bbox property
			Pattern bboxCoordinatePattern = Pattern.compile("(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)");
			// Only tags of the ocr_line class are interesting
			StartTag ocrWordTag = source.getNextStartTag(0, "class", "ocrx_word", false);
			while(ocrWordTag != null) {
				Element wordElement = ocrWordTag.getElement();
				Matcher bboxMatcher = bboxPattern.matcher(wordElement.getAttributeValue("title"));
				if(bboxMatcher.find()) {
					// We found a tag of the ocr_line class containing a bbox property
					Matcher bboxCoordinateMatcher = bboxCoordinatePattern.matcher(bboxMatcher.group());
					bboxCoordinateMatcher.find();
					int[] coordinates = {Integer.parseInt((bboxCoordinateMatcher.group(1))),
							Integer.parseInt((bboxCoordinateMatcher.group(2))),
							Integer.parseInt((bboxCoordinateMatcher.group(3))),
							Integer.parseInt((bboxCoordinateMatcher.group(4)))};
					String word = wordElement.getContent().getTextExtractor().toString();

					float bboxWidthPt = (coordinates[2] - coordinates[0]) / dotsPerPointX;
					float bboxHeightPt = (coordinates[3] - coordinates[1]) / dotsPerPointY;

					// Put the text into the PDF
					cb.beginText();
					// Comment the next line to debug the PDF output (visible Text)
					cb.setTextRenderingMode(PdfContentByte.TEXT_RENDER_MODE_INVISIBLE);
					// Scale the text width to fit the OCR bbox
					boolean textScaled = false;
					do {
						float lineWidth = defaultFont.getBaseFont().getWidthPoint(word, bboxHeightPt);
						if(lineWidth < bboxWidthPt){
							textScaled = true;
						} else {
							bboxHeightPt-=0.1f;
						}
					} while (textScaled==false);

					//put text in the document
					cb.setFontAndSize(defaultFont.getBaseFont(), bboxHeightPt);
					cb.moveText((coordinates[0] / dotsPerPointX), ((pageImagePixelHeight - coordinates[3]) / dotsPerPointY));
					cb.showText(word);
					cb.endText();
				}
				ocrWordTag = source.getNextStartTag(ocrWordTag.getEnd(), "class", "ocrx_word", false);
			}
			pdfDocument.close();
			pdfWriter.close();
		} catch (DocumentException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
