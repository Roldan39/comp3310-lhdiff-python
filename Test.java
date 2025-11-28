public void fileReader(String path){
FileReader fr = new FileReader(path);
LineReader Ir = new LineReader(fr);
String line = null;
while(line = Ir.readLine()){
System.out.println("line");
}
}