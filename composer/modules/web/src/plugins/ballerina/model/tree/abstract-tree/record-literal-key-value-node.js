/**
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import _ from 'lodash';
import Node from '../node';

class AbstractRecordLiteralKeyValueNode extends Node {


    setValue(newValue, silent, title) {
        const oldValue = this.value;
        title = (_.isNil(title)) ? `Modify ${this.kind}` : title;
        this.value = newValue;

        this.value.parent = this;

        if (!silent) {
            this.trigger('tree-modified', {
                origin: this,
                type: 'modify-node',
                title,
                data: {
                    attributeName: 'value',
                    newValue,
                    oldValue,
                },
            });
        }
    }

    getValue() {
        return this.value;
    }


    setKey(newValue, silent, title) {
        const oldValue = this.key;
        title = (_.isNil(title)) ? `Modify ${this.kind}` : title;
        this.key = newValue;

        this.key.parent = this;

        if (!silent) {
            this.trigger('tree-modified', {
                origin: this,
                type: 'modify-node',
                title,
                data: {
                    attributeName: 'key',
                    newValue,
                    oldValue,
                },
            });
        }
    }

    getKey() {
        return this.key;
    }


}

export default AbstractRecordLiteralKeyValueNode;
