<?php

require_once dirname(__FILE__) . "/../../../vendor/autoload.php";
require_once dirname(__FILE__) . "/thrift/ConcourseService.php";
require_once dirname(__FILE__) . "/thrift/shared/Types.php";
require_once dirname(__FILE__) . "/thrift/data/Types.php";
require_once dirname(__FILE__) . "/Link.php";
require_once dirname(__FILE__) . "/Tag.php";
require_once dirname(__FILE__) . "/Link.php";

use Thrift\Shared\Type;
use Thrift\Data\TObject;

define('BIG_ENDIAN', pack('L', 1) === pack('N', 1));
define('MAX_INT', 2147483647);
define('MIN_INT', -2147483648);

class Convert {

    public static function phpToThrift($value) {
        if(is_bool($value)){
            $type = Type::BOOLEAN;
            $data = pack('c', $value == 1 ? 1 : 0);
            print_r($data);
        }
        else if (is_int($value)) {
            if ($value > MAX_INT || $value < $MIN_INT) {
                $type = Type::LONG;
                $data = pack('q', $value);
            }
            else {
                $type = Type::INTEGER;
                $data = pack('l', $value);
            }
            if (!BIG_ENDIAN) {
                $data = strrev($data);
            }
        }
        else if (is_float($value)) {
            $type = Type::FLOAT;
            $data = pack('f', $value);
            if (!BIG_ENDIAN) {
                $data = strrev($data);
            }
            //TODO what about double?
        }
        else if(@get_class($value) == "Tag"){
            $type = Type::TAG;
            $data = utf8_encode(strval($value));
        }
        else if(@get_class($value) == "Link"){
            $type = Type::LINK;
            $data = pack('q', $value);
            if (!BIG_ENDIAN) {
                $data = strrev($data);
            }
        }
        else {
            $type = Type::STRING;
            $data = utf8_encode(strval($value));
        }
        return new TObject(array('type' => $type, 'data' => $data));
    }
        
    public static function stringToTime($time){
        return strtotime($time) * 1000;
    }
    
    public static function thriftToPhp(TObject $tobject){
        $php = null;
        switch ($tobject->type){
            case Type::BOOLEAN:
                $php = unpack('c', $tobject->data)[1];
                $php = $php == 1 ? true : false;
                break;
            case Type::INTEGER:
                $data = !BIG_ENDIAN ? strrev($tobject->data) : $data;
                $php = unpack('l', $data)[1];
                break;
            case Type::LONG:
                $data = !BIG_ENDIAN ? strrev($tobject->data) : $data;
                $php = unpack('q', $data)[1];
                break;
            case Type::FLOAT:
                $data = !BIG_ENDIAN ? strrev($tobject->data) : $data;
                $php = unpack('f', $tdata);
                break;
            case Type::TAG:
                $php = utf8_decode($tobject->data);
                $php = Tag::create($php);
                break;
            case Type::LINK:
                $data = !BIG_ENDIAN ? strrev($tobject->data) : $data;
                $php = unpack('q', $data)[1];
                $php = Link::to($php);
            case Type::STRING:
                $php = utf8_decode($tobject->data);
                break;
            default:
                break;
        }
        return $php;
    }
    
    public static function phpify($data){
        
    }

}
