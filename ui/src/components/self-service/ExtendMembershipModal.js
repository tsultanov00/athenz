/*
 * Copyright The Athenz Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import React from 'react';
import styled from '@emotion/styled';
import AddModal from '../modal/AddModal';
import FlatPicker from '../flatpicker/FlatPicker';
import InputLabel from '../denali/InputLabel';
import { colors } from '../denali/styles';
import DateUtils from '../utils/DateUtils';

const SectionsDiv = styled.div`
    background-color: ${colors.white};
    text-align: left;
    width: 600px;
`;

const SectionDiv = styled.div`
    align-items: flex-start;
    display: flex;
    flex-flow: row nowrap;
    padding: 10px 30px;
`;

const StyledInputLabel = styled(InputLabel)`
    flex: 0 0 120px;
    font-weight: 600;
    line-height: 36px;
`;

const ContentDiv = styled.div`
    display: flex;
    flex: 1 1;
    flex-flow: column nowrap;
`;

const ValueText = styled.div`
    color: ${colors.grey800};
    font: 300 14px HelveticaNeue-Reg, Helvetica, Arial, sans-serif;
    line-height: 36px;
`;

const MaxText = styled.div`
    color: ${colors.grey600};
    font: 300 13px HelveticaNeue-Reg, Helvetica, Arial, sans-serif;
    padding: 0 30px 12px 150px;
`;

const FlatPickrInputDiv = styled.div`
    max-width: 500px;
    width: 260px;
    & > div input {
        background-color: rgba(53, 112, 244, 0.05);
        border-color: transparent;
        border-image: initial;
        border-radius: 2px;
        border-style: solid;
        border-width: 2px;
        box-shadow: none;
        color: rgb(48, 48, 48);
        flex: 1 0 auto;
        font: 300 14px HelveticaNeue-Reg, Helvetica, Arial, sans-serif;
        margin: 0 10px 0 0;
        min-width: 50px;
        outline: none;
        padding: 0.6em 12px;
        position: relative;
        text-align: left;
        width: 80%;
    }
`;

const MS_PER_DAY = 24 * 60 * 60 * 1000;

const formatDate = (date) =>
    date.toLocaleDateString('en-GB', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
    });

export default class ExtendMembershipModal extends React.Component {
    constructor(props) {
        super(props);
        this.dateUtils = new DateUtils();
        this.onSubmit = this.onSubmit.bind(this);
        this.state = {
            expiry: '',
            errorMessage: null,
        };
    }

    // effective member expiry days for the role, already computed by ZMS as the
    // lowest of the domain and role setting (0 means there is no configured max)
    maxDays() {
        const days = Number(this.props.item?.maxExpiryDays);
        return Number.isFinite(days) && days > 0 ? days : 0;
    }

    maxDate() {
        const days = this.maxDays();
        return days ? new Date(Date.now() + days * MS_PER_DAY) : null;
    }

    onSubmit() {
        if (!this.state.expiry || this.state.expiry.length === 0) {
            this.setState({
                errorMessage:
                    'Pick a new expiry date to extend your membership.',
            });
            return;
        }
        this.props.onSubmit(
            this.dateUtils.uxDatetimeToRDLTimestamp(this.state.expiry)
        );
    }

    render() {
        const item = this.props.item;
        if (!item) {
            return null;
        }
        const maxDays = this.maxDays();
        const maxDate = this.maxDate();
        const sections = (
            <SectionsDiv data-testid='extend-membership-form'>
                <SectionDiv>
                    <StyledInputLabel>Role</StyledInputLabel>
                    <ContentDiv>
                        <ValueText>{item.name}</ValueText>
                    </ContentDiv>
                </SectionDiv>
                <SectionDiv>
                    <StyledInputLabel>Domain</StyledInputLabel>
                    <ContentDiv>
                        <ValueText>{item.domainName}</ValueText>
                    </ContentDiv>
                </SectionDiv>
                <SectionDiv>
                    <StyledInputLabel htmlFor='self-serve-extend-expiry'>
                        New expiry
                    </StyledInputLabel>
                    <ContentDiv>
                        <FlatPickrInputDiv>
                            <FlatPicker
                                onChange={(expiry) => {
                                    this.setState({
                                        expiry,
                                        errorMessage: null,
                                    });
                                }}
                                maxDate={maxDate}
                                id='self-serve-extend-expiry'
                                clear={this.state.expiry}
                            />
                        </FlatPickrInputDiv>
                    </ContentDiv>
                </SectionDiv>
                <MaxText data-testid='extend-max-text'>
                    {maxDays
                        ? `You can extend by up to ${maxDays} day${
                              maxDays === 1 ? '' : 's'
                          } (until ${formatDate(maxDate)}).`
                        : 'No maximum expiry is configured, so you can pick any future date.'}
                </MaxText>
            </SectionsDiv>
        );

        return (
            <AddModal
                isOpen={this.props.isOpen}
                cancel={this.props.onCancel}
                submit={this.onSubmit}
                title={`Extend membership: ${item.name}`}
                errorMessage={
                    this.state.errorMessage || this.props.errorMessage
                }
                sections={sections}
                width='660px'
            />
        );
    }
}
