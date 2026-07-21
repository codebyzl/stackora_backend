package org.victor.stackora;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("org.victor.stackora.mapper")
public class StackoraBackendApplication {

    public static void main(String[] args) {

        SpringApplication.run(StackoraBackendApplication.class, args);
        System.out.println("""
                                          ___                                     ,--,
               ,---.  ,--,              ,--.'|_                                 ,--.'|
              /__./|,--.'|              |  | :,'   ,---.    __  ,-.       ,----,|  | :
         ,---.;  ; ||  |,               :  : ' :  '   ,'\\ ,' ,'/ /|     .'   .`|:  : '
        /___/ \\  | |`--'_       ,---. .;__,'  /  /   /   |'  | |' |  .'   .'  .'|  ' |
        \\   ;  \\ ' |,' ,'|     /     \\|  |   |  .   ; ,. :|  |   ,',---, '   ./ '  | |
         \\   \\  \\: |'  | |    /    / ':__,'| :  '   | |: :'  :  /  ;   | .'  /  |  | :
          ;   \\  ' .|  | :   .    ' /   '  : |__'   | .; :|  | '   `---' /  ;--,'  : |__
           \\   \\   ''  : |__ '   ; :__  |  | '.'|   :    |;  : |     /  /  / .`||  | '.'|
            \\   `  ;|  | '.'|'   | '.'| ;  :    ;\\   \\  / |  , ;   ./__;     .' ;  :    ;
             :   \\ |;  :    ;|   :    : |  ,   /  `----'   ---'    ;   |  .'    |  ,   /
              '---" |  ,   /  \\   \\  /   ---`-'                    `---'         ---`-'
                     ---`-'    `----'
        """);
    }

}
